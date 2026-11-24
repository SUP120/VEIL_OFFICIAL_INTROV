// firebase emulators:exec --only auth,firestore --project demo-veil-people "node scripts/check-veil-people.cjs"
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { createRequire } = require("node:module");
const { resolve } = require("node:path");
const fromFunctions = createRequire(resolve(__dirname, "../functions/package.json"));
const { initializeApp } = fromFunctions("firebase-admin/app");
const { getAuth } = fromFunctions("firebase-admin/auth");
const { getFirestore } = fromFunctions("firebase-admin/firestore");

const projectId = "demo-veil-people";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok([authHost, firestoreHost].every(value => /^(127\.0\.0\.1|localhost):\d+$/.test(value)));
const database = `projects/${projectId}/databases/(default)/documents`;
const root = `http://${firestoreHost}/v1/${database}`;

async function request(url, token, body, expected = 200) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(url, { method: body ? "POST" : "GET", headers, body: body && JSON.stringify(body) });
    const result = await response.json();
    assert.equal(response.status, expected, JSON.stringify(result));
    return result;
}

async function user(uid) {
    const email = `${uid}@example.test`;
    const password = "synthetic-password";
    await getAuth().createUser({ uid, email, password });
    const result = await request(
        `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`,
        null, { email, password, returnSecureToken: true }
    );
    return result.idToken;
}

function put(path, data) {
    const fields = Object.fromEntries(Object.entries(data).map(([key, value]) => [
        key, typeof value === "boolean" ? { booleanValue: value } : { stringValue: value }
    ]));
    return { update: { name: `${database}/${path}`, fields } };
}

const remove = path => ({ delete: `${database}/${path}` });
const commit = (token, writes, expected) => request(`${root}:commit`, token, { writes }, expected);
const settings = (uid, username, listed = true) => put(`peopleSettings/${uid}`, { username, listed });
const profile = (uid, alias, username) =>
    put(`peopleProfiles/${uid}`, { alias, searchAlias: alias.toLowerCase(), username });
const claim = (uid, username) => put(`peopleUsernames/${username}`, { uid });

async function main() {
    initializeApp({ projectId });
    const db = getFirestore();
    const alex = await user("alex");
    const river = await user("river");
    const incomplete = await user("incomplete");
    await db.doc("users/alex").set({ profileComplete: true, alias: "Alex" });
    await db.doc("users/river").set({ profileComplete: true, alias: "River" });
    await db.doc("users/incomplete").set({ profileComplete: false });

    await commit(alex, [settings("alex", ""), profile("alex", "Alex", "")]);
    await commit(alex, [claim("alex", "quiet_alex"), settings("alex", "quiet_alex"), profile("alex", "Alex", "quiet_alex")]);
    await commit(river, [claim("river", "quiet_alex"), settings("river", "quiet_alex"), profile("river", "River", "quiet_alex")], 403);
    await commit(river, [claim("river", "moderator"), settings("river", "moderator"), profile("river", "River", "moderator")], 403);
    await commit(river, [settings("river", ""), put("peopleProfiles/river", {
        alias: "River", searchAlias: "river", username: "", email: "private@example.test"
    })], 403);
    await commit(incomplete, [settings("incomplete", ""), profile("incomplete", "Incomplete", "")], 403);
    await request(`${root}/peopleSettings/alex`, river, undefined, 403);
    await request(`${root}/peopleUsernames/quiet_alex`, river, undefined, 403);
    await request(`${root}/peopleProfiles/alex`, null, undefined, 403);

    await db.doc("swipeProfiles/alex").set({ alias: "Alex" });
    await commit(alex, [
        put("users/alex", { profileComplete: true, alias: "Alex Rowan" }),
        put("swipeProfiles/alex", { alias: "Alex Rowan" }),
        profile("alex", "Alex Rowan", "quiet_alex")
    ]);
    assert.equal((await db.doc("peopleSettings/alex").get()).get("username"), "quiet_alex");

    const search = {
        structuredQuery: {
            from: [{ collectionId: "peopleProfiles" }],
            orderBy: [{ field: { fieldPath: "username" }, direction: "ASCENDING" }],
            startAt: { values: [{ stringValue: "quiet" }], before: true },
            endAt: { values: [{ stringValue: "quiet\uF8FF" }], before: false },
            limit: 24
        }
    };
    const matches = await request(`${root}:runQuery`, river, search);
    assert.equal(matches[0].document.fields.alias.stringValue, "Alex Rowan");
    assert.equal(matches[0].document.fields.searchAlias.stringValue, "alex rowan");
    assert.deepEqual(Object.keys(matches[0].document.fields).sort(), ["alias", "searchAlias", "username"]);

    await commit(alex, [settings("alex", "quiet_alex", false), remove("peopleProfiles/alex")]);
    await commit(alex, [
        put("users/alex", { profileComplete: true, alias: "Alex Reed" }),
        put("swipeProfiles/alex", { alias: "Alex Reed" })
    ]);
    assert.equal((await db.doc("peopleSettings/alex").get()).get("listed"), false);
    const hidden = await request(`${root}:runQuery`, river, search);
    assert.ok(hidden.every(result => !result.document));
    await commit(river, [claim("river", "quiet_alex"), settings("river", "quiet_alex"), profile("river", "River", "quiet_alex")], 403);

    await commit(alex, [claim("alex", "still_alex"), settings("alex", "still_alex", false), remove("peopleProfiles/alex")], 403);
    await commit(alex, [claim("alex", "still_alex"), settings("alex", "still_alex", false),
        remove("peopleProfiles/alex"), remove("peopleUsernames/quiet_alex")]);
    await commit(river, [claim("river", "quiet_alex"), settings("river", "quiet_alex"), profile("river", "River", "quiet_alex")]);

    await user("older");
    await db.doc("users/older").set({ profileComplete: true, alias: "Older member", dob: "2000-01-01" });
    const backfill = resolve(__dirname, "backfill-veil-people.cjs");
    const runBackfill = args => execFileSync(process.execPath, [backfill, `--project=${projectId}`, ...args], {
        encoding: "utf8"
    });
    assert.match(runBackfill([]), /Would add 1 People entries/);
    assert.equal((await db.doc("peopleProfiles/older").get()).exists, false);
    assert.match(runBackfill(["--apply"]), /Added 1 People entries/);
    assert.deepEqual((await db.doc("peopleProfiles/older").get()).data(), {
        alias: "Older member", searchAlias: "older member", username: ""
    });
    assert.equal((await db.doc("peopleProfiles/alex").get()).exists, false);
    assert.match(runBackfill(["--apply"]), /Added 0 People entries/);
    console.log("People emulator checks passed.");
}

main().catch(error => { console.error(error); process.exitCode = 1; });
