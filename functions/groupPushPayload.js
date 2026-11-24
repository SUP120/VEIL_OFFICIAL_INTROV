const publicId = /^[A-Za-z0-9_-]{1,128}$/;
const isId = (value) => typeof value === "string" && publicId.test(value);

function makeGroupPushData(roomId, room, messageId, message, recipientUid, sentAtMs) {
    return {
        type: "group_chat_message",
        roomId,
        roomName: String(room.name || "VEIL group").slice(0, 80),
        messageId,
        recipientUid,
        senderUid: message.uid,
        senderAlias: String(message.alias || "Group member").slice(0, 80),
        preview: message.text.trim().slice(0, 160),
        sentAt: String(sentAtMs),
    };
}

function groupPushAllowed(mode, recipient, replyAuthor, incomingBlocked, outgoingBlocked) {
    return mode !== "muted" && (mode !== "replies" || recipient === replyAuthor)
        && !incomingBlocked && !outgoingBlocked;
}

module.exports = { isId, makeGroupPushData, groupPushAllowed };
