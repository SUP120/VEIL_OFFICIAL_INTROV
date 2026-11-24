const assert = require("node:assert/strict");
const { resolve } = require("node:path");
const { createRequire } = require("node:module");
const fromFunctions = createRequire(resolve(__dirname, "../functions/package.json"));
const { initializeApp } = fromFunctions("firebase-admin/app");
const { getAuth } = fromFunctions("firebase-admin/auth");
const { getFirestore } = fromFunctions("firebase-admin/firestore");
const projectId = "demo-veil-groups";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
assert.ok([authHost, firestoreHost].every(host => /^(127\.0\.0\.1|localhost):\d+$/.test(host)));
const root = `projects/${projectId}/databases/(default)/documents`;
const api = `http://${firestoreHost}/v1/${root}`;
const room = "group_rooms/test-room";
const msg = `${room}/messages/first`;
const actorTime = new Date().toISOString();

async function client(uid) {
    const email = `${uid}@example.test`;
    const password = "synthetic-password";
    await getAuth().createUser({ uid, email, password });
    const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
    });
    assert.equal(response.status, 200);
    return (await response.json()).idToken;
}

function value(data) {
    if (data === null) return { nullValue: null };
    if (data instanceof Date) return { timestampValue: data.toISOString() };
    if (Array.isArray(data)) return { arrayValue: { values: data.map(value) } };
    if (typeof data === "number") return { integerValue: String(data) };
    if (typeof data === "boolean") return { booleanValue: data };
    if (typeof data === "object") return { mapValue: { fields: fields(data) } };
    return { stringValue: data };
}
const fields = data => Object.fromEntries(Object.entries(data).map(([key, entry]) => [key, value(entry)]));
const name = path => `${root}/${path}`;
const update = (path, data, extra = {}) => ({ update: { name: name(path), fields: fields(data) }, ...extra });
const patch = (path, data, extra = {}) => update(path, data, { updateMask: { fieldPaths: Object.keys(data) }, ...extra });
const newMessage = (uid, text = "Hello group", time = new Date()) => ({ uid, alias: uid, text, sentAt: time });

async function request(token, path, expected, writes) {
    const response = await fetch(`${api}${path}`, {
        method: writes ? "POST" : "GET",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        ...(writes ? { body: JSON.stringify({ writes }) } : {}),
    });
    const message = await response.text();
    assert.equal(response.status, expected, message);
}
const write = (token, expected, ...writes) => request(token, ":commit", expected, writes);
const read = (token, path, expected) => request(token, `/${path}`, expected);

async function main() {
    initializeApp({ projectId });
    const db = getFirestore();
    const [owner, newcomer, visitor] = await Promise.all(["owner", "newcomer", "visitor"].map(client));
    const initial = { createdBy: "owner", joinMode: "approve", memberUids: ["owner"], pendingUids: [], memberCount: 1, isSystem: false, name: "Test room" };
    await write(visitor, 403, update(room, initial));
    await write(owner, 200, update(room, initial));
    await read(visitor, room, 200);
    await write(owner, 200, update(msg, newMessage("owner")));
    await read(visitor, msg, 403);
    await write(visitor, 403, update(`${room}/messages/injected`, newMessage("visitor")));
    await write(visitor, 403, patch(room, { pendingUids: ["visitor"], name: "Forged" }));
    await write(newcomer, 200, patch(room, { pendingUids: ["newcomer"] }));
    await write(visitor, 403, patch(room, { memberUids: ["owner", "visitor"], memberCount: 2 }));
    await write(visitor, 403, patch(room, { memberUids: ["owner", "newcomer"], memberCount: 2, pendingUids: [] }));
    await write(owner, 200, patch(room, { memberUids: ["owner", "newcomer"], memberCount: 2, pendingUids: [] }));
    await read(newcomer, msg, 200);
    await write(newcomer, 403, update(`${room}/messages/spoofed`, newMessage("owner")));
    await write(newcomer, 403, update(`${room}/messages/oversized`, newMessage("newcomer", "x".repeat(2001))));
    const message = { uid: "newcomer", alias: "Newcomer", text: "A private reply", replyToId: "first", replyToAlias: "owner", replyToText: "Hello group", pushViaRelay: true };
    const serverTime = (fieldPath) => ({ fieldPath, setToServerValue: "REQUEST_TIME" });
    await write(newcomer, 200,
        update(`${room}/messages/reply`, message, { updateTransforms: [serverTime("sentAt")] }),
        patch(room, { lastMsg: "New message", lastMsgUid: "newcomer" }, { updateTransforms: [serverTime("lastMsgAt")] }));
    await write(newcomer, 403, patch(room, { lastMsg: "A private reply", lastMsgAt: new Date(actorTime) }));
    await write(newcomer, 200, patch(msg, { reactions: { newcomer: "❤️" } }));
    await write(visitor, 403, patch(msg, { reactions: { visitor: "🙌", newcomer: "❤️" } }));
    await write(newcomer, 403, patch(msg, { text: "Replaced!", reactions: { newcomer: "😂" } }));
    await write(owner, 403, patch(msg, { reactions: { newcomer: "😂" } }));
    await write(owner, 200, patch(msg, { reactions: { newcomer: "❤️", owner: "🙌" } }));
    await write(newcomer, 200, patch(msg, { reactions: { owner: "🙌" } }));
    await write(owner, 403, patch(msg, { reactions: { owner: "invalid" } }));
    await write(newcomer, 403, patch(room, { announcement: "Fake announcement" }));
    await write(owner, 200, patch(room, { announcement: "Introductions on Friday" }));
    const pref = "users/newcomer/group_chat_state/test-room";
    await write(newcomer, 200, update(pref, { notificationMode: "replies", pinned: true, lastReadAt: new Date() }));
    await read(visitor, pref, 403);
    await write(visitor, 403, patch(pref, { notificationMode: "muted" }));
    await write(newcomer, 403, patch(pref, { notificationMode: "unknown" }));
    const device = "group_push_devices/newcomer";
    await write(newcomer, 200, update(device, { token: "fake-device" }, { updateTransforms: [serverTime("updatedAt")] }));
    await read(newcomer, device, 403);
    await read(visitor, device, 403);
    await write(visitor, 403, update(device, { token: "forged-device" }, { updateTransforms: [serverTime("updatedAt")] }));
    await write(newcomer, 200, patch(room, { memberUids: ["owner"], memberCount: 1 }));
    await read(newcomer, msg, 403);
    await write(newcomer, 403, update(`${room}/messages/after-leaving`, newMessage("newcomer")));
    await write(owner, 200, patch(room, { joinMode: "auto" }));
    await write(visitor, 200, patch(room, { memberUids: ["owner", "visitor"], memberCount: 2 }));
    await db.doc("safetyRestrictions/visitor").set({ permanent: true });
    await write(visitor, 403, update(`${room}/messages/restricted`, newMessage("visitor")));
    await read(visitor, msg, 200);
    console.log("Group rules passed: membership, approval, visibility, atomic send, reactions, settings, and device-token privacy.");
}

main().catch(error => { console.error(error); process.exitCode = 1; });
