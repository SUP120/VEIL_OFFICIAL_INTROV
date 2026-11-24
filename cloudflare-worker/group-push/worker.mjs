import groupPush from "../../functions/groupPushPayload.js";
import { GoogleError, googleGroupClient, verifyAppAndAccount } from "./google.mjs";

const { isId, makeGroupPushData, groupPushAllowed } = groupPush;
const PUSH_BATCH = 25;
const PENDING_LEASE_MS = 40000;
const FRESH_PERIOD_MS = 10 * 60 * 1000;

async function readInput(request) {
    const reader = request.body?.getReader();
    if (!reader) return null;
    const readerBytes = [];
    let length = 0;
    const text = new TextDecoder();
    for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        length += value.length;
        if (length > 512) {
            await reader.cancel();
            return null;
        }
        readerBytes.push(text.decode(value, { stream: true }));
    }
    return JSON.parse(readerBytes.join("") + text.decode());
}

export function createGroupPushHandler({
    authorize = verifyAppAndAccount,
    connect = googleGroupClient,
    clock = Date.now,
} = {}) {
    return async (request, env) => {
        const path = new URL(request.url).pathname;
        if (path === "/health" && request.method === "GET") {
            const configured = env.PROJECT_ID && env.PROJECT_NUMBER && env.ANDROID_APP_ID && env.GROUP_PUSH_SERVICE_ACCOUNT;
            return new Response(configured ? "ready" : "configuration required", { status: configured ? 200 : 503 });
        }
        if (path !== "/group-message" || request.method !== "POST") return new Response(null, { status: 404 });
        let input;
        try {
            input = await readInput(request);
        } catch {
            return new Response(null, { status: 400 });
        }
        if (!isId(input?.roomId) || !isId(input?.messageId)) return new Response(null, { status: 400 });
        try {
            const uid = await authorize(request, env);
            if (!isId(uid)) return new Response(null, { status: 401 });
            const db = await connect(env);
            const status = await deliver(db, uid, input.roomId, input.messageId, clock());
            return new Response(null, { status });
        } catch (error) {
            console.warn("Group delivery unavailable", { status: error instanceof GoogleError ? error.status : null });
            return new Response(null, { status: 503 });
        }
    };
}

async function deliver(db, uid, roomId, messageId, now) {
    const roomPath = `group_rooms/${roomId}`;
    const messagePath = `${roomPath}/messages/${messageId}`;
    const deliveryPath = `group_push_delivery/${roomId}/messages/${messageId}`;
    const [roomDoc, messageDoc, state, restriction] = await db.getAll([
        roomPath, messagePath, deliveryPath, `safetyRestrictions/${uid}`,
    ]);
    const room = roomDoc?.data;
    const message = messageDoc?.data;
    if (!Array.isArray(room?.memberUids) || !room.memberUids.includes(uid) || message?.uid !== uid
        || message.pushViaRelay !== true || typeof message.text !== "string" || !message.text.trim()) return 403;
    if (restriction && (restriction.data.permanent !== false || !Number.isFinite(restriction.data.until)
        || restriction.data.until > now)) return 403;
    if (state?.data.status === "done") return 204;
    if (!Number.isFinite(message.sentAt) || now - message.sentAt > FRESH_PERIOD_MS || message.sentAt > now + 30000) return 204;
    if ((state?.data.leaseUntil ?? 0) > now) return 409;

    const lastUid = state?.data.cursor || "";
    const recipients = [...new Set(room.memberUids)].filter(
        (recipient) => isId(recipient) && recipient !== uid && recipient > lastUid,
    ).sort();
    const pending = recipients.slice(0, PUSH_BATCH);
    let version;
    try {
        version = await db.updateDelivery(deliveryPath, {
            status: "pending", cursor: lastUid, leaseUntil: now + PENDING_LEASE_MS,
        }, state?.version);
    } catch (error) {
        if (error instanceof GoogleError && [400, 409, 412].includes(error.status)) return 409;
        throw error;
    }

    try {
        const quotedPath = isId(message.replyToId) ? `${roomPath}/messages/${message.replyToId}` : null;
        const paths = pending.flatMap((recipient) => [
            `group_push_devices/${recipient}`,
            `users/${recipient}/group_chat_state/${roomId}`,
            `userBlocks/${uid}/blocked/${recipient}`,
            `userBlocks/${recipient}/blocked/${uid}`,
        ]);
        if (quotedPath) paths.push(quotedPath);
        const documents = await db.getAll(paths);
        const replyAuthor = quotedPath ? documents.at(-1)?.data.uid : null;
        const deliveries = pending.flatMap((recipient, index) => {
            const [device, preference, outgoingBlock, incomingBlock] = documents.slice(index * 4, index * 4 + 4);
            const token = device?.data.token;
            if (typeof token !== "string" || !token
                || !groupPushAllowed(preference?.data.notificationMode, recipient, replyAuthor, !!incomingBlock, !!outgoingBlock)) return [];
            return [{ token, data: makeGroupPushData(roomId, room, messageId, message, recipient, message.sentAt) }];
        });
        for (let index = 0; index < deliveries.length; index += 5) {
            const results = await Promise.allSettled(deliveries.slice(index, index + 5).map(({ token, data }) => db.push(token, data)));
            const failure = results.find((result) => result.status === "rejected");
            if (failure) throw failure.reason;
        }
        const finished = recipients.length <= PUSH_BATCH;
        await db.updateDelivery(deliveryPath, {
            status: finished ? "done" : "pending",
            cursor: pending.at(-1) ?? lastUid,
            leaseUntil: 0,
        }, version);
        return finished ? 204 : 202;
    } catch (error) {
        await db.updateDelivery(deliveryPath, { status: "pending", cursor: lastUid, leaseUntil: 0 }, version).catch(() => {});
        throw error;
    }
}

export default { fetch: createGroupPushHandler() };
