// Run through Firebase emulators:exec in the demo-veil-safety project.
const assert = require("node:assert/strict");
const { createRequire } = require("node:module");
const { resolve } = require("node:path");
const fromFunctions = createRequire(resolve(__dirname, "../functions/package.json"));
const { initializeApp } = fromFunctions("firebase-admin/app");
const { getAuth } = fromFunctions("firebase-admin/auth");
const { getFirestore } = fromFunctions("firebase-admin/firestore");

const projectId = "demo-veil-safety";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok(authHost && firestoreHost, "Run against the local Auth and Firestore emulators only");
assert.ok([authHost, firestoreHost].every((host) => /^(127\.0\.0\.1|localhost):\d+$/.test(host)));
const clientRoot = `http://${firestoreHost}/v1/projects/${projectId}/databases/(default)/documents`;
const callableRoot = `http://127.0.0.1:5001/${projectId}/asia-south1`;

async function request(url, { method = "GET", token, data } = {}) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(url, { method, headers, body: data ? JSON.stringify(data) : undefined });
    return { status: response.status, body: await response.json() };
}

async function signIn(uid, moderator = false) {
    const email = `${uid}@example.test`;
    const password = "demo-password-only";
    await getAuth().createUser({ uid, email, password });
    if (moderator) await getAuth().setCustomUserClaims(uid, { safetyModerator: true });
    const { status, body } = await request(
        `http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`,
        { method: "POST", data: { email, password, returnSecureToken: true } }
    );
    assert.equal(status, 200, JSON.stringify(body));
    return body.idToken;
}

function call(name, token, data) {
    return request(`${callableRoot}/${name}`, { method: "POST", token, data: { data } });
}

function document(path, token, fields) {
    return request(`${clientRoot}/${path}`, {
        method: fields ? "PATCH" : "GET", token,
        data: fields ? { fields: Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, { stringValue: value }])) } : undefined
    });
}

async function expectStatus(promise, expected) {
    const response = await promise;
    assert.equal(response.status, expected, JSON.stringify(response.body));
    return response.body;
}

async function main() {
    initializeApp({ projectId });
    const db = getFirestore();
    const reporter = await signIn("reporter");
    const target = await signIn("target");
    const moderator = await signIn("moderator", true);
    await db.doc("users/target").set({ alias: "Sample account" });
    await db.doc("peopleProfiles/target").set({ alias: "Sample account", searchAlias: "sample account", username: "" });
    await db.doc("reports/group__reporter__target").set({ reporterUid: "reporter", targetUid: "target" });
    await db.doc("matches/testMatch").set({ userA: "reporter", userB: "target" });
    await db.doc("matches/testMatch/messages/original").set({ senderUid: "target", text: "Synthetic test message", sentAt: 100 });
    await expectStatus(document("reports/group__reporter__target", target), 403);
    await expectStatus(document("reports/group__reporter__target", reporter), 200);
    await expectStatus(document("reports/group__reporter__target", moderator), 200);
    await expectStatus(document("reports/group__reporter__another", reporter), 404);

    const profile = { kind: "profile", category: "harassment", description: "Test account report", reportedUserId: "target" };
    await expectStatus(call("submitSafetyEntry", null, profile), 401);
    await expectStatus(call("submitSafetyEntry", reporter, { ...profile, category: "invalid" }), 400);
    const profileResult = await expectStatus(call("submitSafetyEntry", reporter, profile), 200);
    assert.deepEqual(await expectStatus(call("submitSafetyEntry", reporter, profile), 200), profileResult);
    const profileId = profileResult.result.id;
    await expectStatus(document(`safetyReports/${profileId}`, reporter), 403);
    await expectStatus(document(`safetyReports/${profileId}`, target), 403);
    await expectStatus(document(`safetyReports/${profileId}`, reporter, { status: "reviewed" }), 403);
    await expectStatus(document(`safetyReports/${profileId}`, moderator, { status: "reviewed" }), 403);
    const concern = { kind: "concern", category: "sexual_exploitation", requestId: "concern-1", description: "Synthetic safety concern" };
    await expectStatus(call("submitSafetyEntry", reporter, { ...concern, attachment: "extra field" }), 400);
    const concernId = (await expectStatus(call("submitSafetyEntry", reporter, concern), 200)).result.id;
    assert.equal((await db.doc(`safetyReports/${concernId}`).get()).get("priority"), "high");

    const feedback = { kind: "feedback", category: "bug", requestId: "feedback-1", description: "A test feedback submission" };
    await expectStatus(call("submitSafetyEntry", reporter, { ...feedback, description: "" }), 400);
    await expectStatus(call("submitSafetyEntry", reporter, { ...feedback, description: "https://example.test" }), 400);
    const feedbackId = (await expectStatus(call("submitSafetyEntry", reporter, feedback), 200)).result.id;
    await expectStatus(document(`safetyFeedback/${feedbackId}`, reporter), 403);
    await expectStatus(document(`safetyFeedback/${feedbackId}`, moderator), 200);

    const message = { kind: "message", category: "child_safety", description: "Synthetic safety test", matchId: "testMatch", messageId: "original" };
    const conversation = { kind: "conversation", category: "harassment", description: "Test conversation report", matchId: "testMatch" };
    const conversationId = (await expectStatus(call("submitSafetyEntry", reporter, conversation), 200)).result.id;
    assert.equal((await db.doc(`safetyReports/${conversationId}`).get()).get("conversationId"), "testMatch");
    await expectStatus(call("submitSafetyEntry", moderator, message), 403);
    await expectStatus(call("submitSafetyEntry", target, message), 404);
    const messageId = (await expectStatus(call("submitSafetyEntry", reporter, message), 200)).result.id;
    const messageReport = (await db.doc(`safetyReports/${messageId}`).get()).data();
    assert.equal(messageReport.priority, "high");
    assert.equal(messageReport.reportedUserId, "target");
    assert.deepEqual(messageReport.messageReference, { matchId: "testMatch", messageId: "original", sentAt: 100 });
    assert.doesNotMatch(JSON.stringify(messageReport), /Synthetic test message/);
    await expectStatus(document(`safetyReports/${messageId}`, moderator), 200);

    await expectStatus(document("matches/testMatch/messages/before", target, { senderUid: "target" }), 200);
    await expectStatus(document("matches/testMatch/messages/outsider", moderator, { senderUid: "moderator" }), 403);
    await expectStatus(document("userBlocks/reporter/blocked/target", target, { createdAt: "fake" }), 403);
    const block = await expectStatus(request(`${clientRoot}:commit`, { method: "POST", token: reporter, data: {
        writes: [{ transform: {
            document: `projects/${projectId}/databases/(default)/documents/userBlocks/reporter/blocked/target`,
            fieldTransforms: [{ fieldPath: "createdAt", setToServerValue: "REQUEST_TIME" }]
        } }]
    } }), 200);
    assert.ok(block.commitTime);
    await expectStatus(document("matches/testMatch/messages/original", target), 403);
    await expectStatus(document("matches/testMatch/messages/after", target, { senderUid: "target" }), 403);
    await expectStatus(document("matches/testMatch/messages/after", reporter, { senderUid: "reporter" }), 403);
    await expectStatus(document("friendApproaches/reconnect", target, { fromUid: "target", toUid: "reporter" }), 403);

    const review = { id: messageId, action: "suspend", note: "Synthetic review", removeMessage: true };
    await expectStatus(call("reviewSafetyReport", reporter, review), 403);
    await expectStatus(call("reviewSafetyReport", moderator, review), 200);
    assert.equal((await db.doc("matches/testMatch/messages/original").get()).exists, false);
    assert.equal((await db.doc("safetyRestrictions/target").get()).get("permanent"), false);
    assert.equal((await db.doc("peopleProfiles/target").get()).exists, false);
    await expectStatus(document("veilPosts/whileSuspended", target, { authorUid: "target" }), 403);
    await expectStatus(document("veilPosts/ordinaryAccount", reporter, { authorUid: "reporter" }), 200);
    const audit = await db.collection(`safetyReports/${messageId}/actions`).get();
    assert.equal(audit.size, 1);
    assert.equal(audit.docs[0].get("removedMessage"), true);
    await expectStatus(document(`safetyReports/${messageId}/actions/${audit.docs[0].id}`, reporter), 403);
    await expectStatus(document(`safetyReports/${messageId}/actions/${audit.docs[0].id}`, moderator), 200);
    await expectStatus(call("reviewSafetyReport", moderator, review), 400);
    await expectStatus(call("reviewSafetyReport", moderator, { id: profileId, action: "ban", note: "Synthetic permanent ban" }), 200);
    assert.equal((await db.doc("safetyRestrictions/target").get()).get("permanent"), true);
    console.log("VEIL safety emulator checks passed.");
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
