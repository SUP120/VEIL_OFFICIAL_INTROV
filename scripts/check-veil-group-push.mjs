import assert from "node:assert/strict";
import { createGroupPushHandler } from "../cloudflare-worker/group-push/worker.mjs";
import { GoogleError } from "../cloudflare-worker/group-push/google.mjs";

const at = 1700000000000;
const data = new Map();
let sequence = 0;
const put = (path, value) => data.set(path, { data: value, version: String(++sequence) });
const room = "group_rooms/r";
const msg = `${room}/messages/m`;
const delivery = "group_push_delivery/r/messages/m";
const roomMembers = ["alice", "bob", "cara", "dave", "ella", "fred", ...Array.from({ length: 30 }, (_, index) => `member${index.toString().padStart(2, "0")}`)];
put(room, { name: "Book club", memberUids: roomMembers });
put(msg, { uid: "alice", alias: "Alice", text: "What are you reading?", sentAt: at - 2_000, pushViaRelay: true, replyToId: "quoted" });
put(`${room}/messages/quoted`, { uid: "bob", text: "Hi", sentAt: at - 10_000 });
for (const uid of roomMembers) put(`group_push_devices/${uid}`, { token: `token-${uid}` });
put("users/bob/group_chat_state/r", { notificationMode: "replies" });
put("users/cara/group_chat_state/r", { notificationMode: "replies" });
put("users/dave/group_chat_state/r", { notificationMode: "muted" });
put("userBlocks/alice/blocked/ella", {});
put("userBlocks/fred/blocked/alice", {});

const pushes = [];
let failedOnce = false;
const handler = createGroupPushHandler({
    authorize: async request => request.headers.get("X-Test-User"),
    connect: async () => ({
        getAll: async paths => paths.map(path => data.get(path) ?? null),
        updateDelivery: async (path, values, version) => {
            if (data.get(path)?.version !== version) throw new GoogleError(409);
            put(path, values);
            return data.get(path).version;
        },
        push: async (token, body) => {
            if (token === "token-member00" && !failedOnce) {
                failedOnce = true;
                throw new GoogleError(503);
            }
            pushes.push({ token, body });
        },
    }),
    clock: () => at,
});
const call = (user, id = "m") => handler(new Request("https://worker.example.test/group-message", {
    method: "POST",
    headers: user ? { "X-Test-User": user } : {},
    body: JSON.stringify({ roomId: "r", messageId: id }),
}), {});

assert.equal((await call(null)).status, 401);
assert.equal((await call("bob")).status, 403);
assert.equal((await call("alice", "../bad")).status, 400);
assert.equal((await call("alice")).status, 503);
assert.equal(data.get(delivery).data.leaseUntil, 0);
assert.equal((await call("alice")).status, 202);
assert.equal((await call("alice")).status, 204);
const sentBefore = pushes.length;
assert.equal((await call("alice")).status, 204);
assert.equal(pushes.length, sentBefore);
const recipients = new Set(pushes.map(({ body }) => body.recipientUid));
assert.equal(recipients.size, 31);
assert.deepEqual([...recipients].filter(uid => !uid.startsWith("member")), ["bob"]);
assert.ok(pushes.every(({ token, body }) => token === `token-${body.recipientUid}` && body.messageId === "m" && body.roomId === "r"));
assert.equal(pushes[0].body.preview, "What are you reading?");
put(`${room}/messages/old`, { uid: "alice", text: "An older message", sentAt: at - 700_000, pushViaRelay: true });
assert.equal((await call("alice", "old")).status, 204);
assert.equal(pushes.length, sentBefore);
console.log("Group notification relay passed: authenticated author, exclusions, retries, paging, expiry, and deduplication.");
