// Run with Firebase emulators for Auth and Firestore. This test never calls Groq.
const assert = require("node:assert/strict");
const { createRequire } = require("node:module");
const { randomUUID } = require("node:crypto");
const { resolve } = require("node:path");
const fromFunctions = createRequire(resolve(__dirname, "../functions/package.json"));
const { initializeApp } = fromFunctions("firebase-admin/app");
const { getFirestore } = fromFunctions("firebase-admin/firestore");
const { getAuth } = fromFunctions("firebase-admin/auth");
const { createAiFriendHandler } = require("../functions/aiFriendChat");

const projectId = "demo-veil-ai";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok([authHost, firestoreHost].every(value => /^(127\.0\.0\.1|localhost):\d+$/.test(value)));
const root = `http://${firestoreHost}/v1/projects/${projectId}/databases/(default)/documents`;

async function token(uid) {
    const email = `${uid}@example.test`;
    const password = "synthetic-password";
    await getAuth().createUser({ uid, email, password });
    const response = await fetch(
        `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake`,
        { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password, returnSecureToken: true }) },
    );
    assert.equal(response.status, 200);
    return (await response.json()).idToken;
}

async function client(path, idToken, expected, method = "GET") {
    const response = await fetch(`${root}/${path}`, {
        method,
        headers: { Authorization: `Bearer ${idToken}`, "Content-Type": "application/json" },
        ...(method === "PATCH" ? { body: JSON.stringify({ fields: { content: { stringValue: "tampered" } } }) } : {}),
    });
    assert.equal(response.status, expected, await response.text());
}

const input = (message, extra = {}) => ({ action: "send", friendId: "luna", requestId: randomUUID(), message, ...extra });
const matches = (code, reason) => error => error.code === code && (!reason || error.details?.reason === reason);

async function main() {
    initializeApp({ projectId });
    const db = getFirestore();
    const sender = { uid: "sender" };
    const other = { uid: "other" };
    const [senderToken, otherToken] = await Promise.all([token(sender.uid), token(other.uid)]);
    const user = db.doc("users/sender");
    const usage = user.collection("ai_private").doc("usage");
    await user.set({ cookieBalance: 2 });
    await db.doc("users/other").set({ cookieBalance: 0 });

    let at = Date.now();
    let calls = 0;
    let captured;
    let replyMode = async () => "A friendly synthetic AI reply.";
    const handle = createAiFriendHandler({
        db,
        now: () => at,
        generateReply: async messages => {
            calls++;
            captured = messages;
            return replyMode();
        },
    });
    const send = (data, auth = sender) => handle({ auth, data });
    const status = () => send({ action: "status" });
    const tick = () => { at += 3000; };

    await assert.rejects(handle({ data: { action: "status" } }), matches("unauthenticated"));
    await assert.rejects(send(input("Wrong persona", { friendId: "__proto__" })), matches("invalid-argument"));
    await assert.rejects(send(input("Too long".repeat(100))), matches("invalid-argument"));
    assert.deepEqual(await status(), { freeLimit: 20, freeRemaining: 20, cookieCost: 1, cookieBalance: 2 });

    const legacy = user.collection("ai_conversations").doc("legacyRoom");
    await legacy.set({ friendId: "sage", archived: false, messageCount: 0 });
    await assert.rejects(send(input("Wrong room", { conversationId: legacy.id })), matches("not-found"));
    const first = await send(input("Continue with Sage", { friendId: "sage", conversationId: legacy.id }));
    assert.equal(first.conversationId, legacy.id);
    assert.match(captured[0].content, /You are Sage/);
    await assert.rejects(send(input("Too soon")), matches("resource-exhausted"));

    for (let i = 2; i <= 20; i++) {
        tick();
        const result = await send(input(`Free message ${i}`));
        assert.equal(result.usage.freeRemaining, 20 - i);
        assert.equal(result.chargedCookies, 0);
        assert.ok(captured.length <= 10);
        assert.match(captured[0].content, /You are Luna/);
    }
    assert.equal(calls, 20);
    assert.equal((await status()).cookieBalance, 2);
    tick();

    const paid = input("One paid reply", { uid: other.uid });
    await assert.rejects(send(paid), matches("failed-precondition", "payment-confirmation"));
    assert.equal(calls, 20);
    let enter;
    let release;
    const providerEntered = new Promise(resolve => { enter = resolve; });
    const providerRelease = new Promise(resolve => { release = resolve; });
    replyMode = async () => {
        enter();
        return providerRelease;
    };
    const pending = send({ ...paid, allowPaid: true });
    await providerEntered;
    await assert.rejects(send({ ...paid, allowPaid: true }), matches("resource-exhausted"));
    await assert.rejects(send(input("Other persona while Luna replies", { friendId: "rex", allowPaid: true })), matches("resource-exhausted"));
    await user.update({ cookieBalance: 2 });
    release("A paid synthetic reply.");
    const charged = await pending;
    assert.equal(charged.chargedCookies, 1);
    assert.equal(charged.usage.cookieBalance, 1);
    assert.deepEqual(await send(paid), charged);
    await assert.rejects(send({ ...paid, message: "Different text" }), matches("invalid-argument"));
    assert.equal(calls, 21);
    assert.equal((await user.get()).get("cookiesSpent"), 1);
    assert.equal((await db.doc("users/other").get()).get("cookieBalance"), 0);

    tick();
    const unsuccessful = input("Provider fails", { allowPaid: true });
    replyMode = async () => { throw new Error("Synthetic upstream error"); };
    await assert.rejects(send(unsuccessful), matches("unavailable"));
    assert.equal((await status()).cookieBalance, 1);
    assert.equal((await usage.get()).get("lease"), null);
    tick();
    replyMode = async () => "Reply after retry";
    await send(unsuccessful);
    assert.equal((await status()).cookieBalance, 0);

    tick();
    await assert.rejects(send(input("No cookies", { allowPaid: true })), matches("failed-precondition", "not-enough-cookies"));
    assert.equal(calls, 23);
    assert.equal((await db.collection("cookieLedger").doc(sender.uid).collection("transactions").get()).size, 2);

    const messageId = charged.messages[1].id;
    const report = { action: "report", conversationId: charged.conversationId, messageId, category: "child_safety" };
    await assert.rejects(send(report, other), matches("not-found"));
    await assert.rejects(send({ ...report, messageId: charged.messages[0].id }), matches("not-found"));
    const { id } = await send(report);
    assert.equal((await send(report)).id, id);
    const reportDoc = await db.doc(`safetyReports/${id}`).get();
    assert.equal(reportDoc.get("aiReply.content"), "A paid synthetic reply.");
    assert.equal(reportDoc.get("queueRank"), 0);

    const messagePath = `users/sender/ai_conversations/luna/messages/${messageId}`;
    await client(messagePath, senderToken, 200);
    await client(messagePath, otherToken, 403);
    await client(messagePath, senderToken, 403, "PATCH");
    await client("users/sender/ai_private/usage", senderToken, 403);
    await client(`safetyReports/${id}`, senderToken, 403);
    const locked = { uid: "locked" };
    await db.doc("users/locked").set({});
    await db.doc("safetyRestrictions/locked").set({ permanent: true });
    await assert.rejects(send({ action: "status" }, locked), matches("permission-denied"));

    await user.update({ cookieBalance: 1 });
    await usage.update({ attemptsToday: 60 });
    await assert.rejects(send(input("Daily cap", { allowPaid: true })), matches("resource-exhausted"));
    assert.equal(calls, 23);
    console.log("VEIL Friends emulator passed: authentication, personas, history, pacing, free allowance, paid confirmation, duplicate sends, provider failure, wallet, daily cap, reports, and private rules.");
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
