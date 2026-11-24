const { FieldValue, Timestamp, getFirestore } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { createHash } = require("node:crypto");

const region = "asia-south1";
const feedbackCategories = new Set(["bug", "suggestion", "account", "other"]);
const urgentCategories = new Set(["child_safety", "sexual_exploitation"]);
const reportCategories = new Set([...urgentCategories, "harassment", "inappropriate", "threats", "spam", "other"]);

function requireUid(request) {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to continue.");
    return request.auth.uid;
}

function validateId(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
        throw new HttpsError("invalid-argument", "Invalid reference.");
    }
    return value;
}

function validateText(value, required) {
    if (typeof value !== "string" || value.length > 1500 || (required && !value.trim())) {
        throw new HttpsError("invalid-argument", "Enter a description of up to 1,500 characters.");
    }
    if (/(?:https?:\/\/|www\.|\bdata:|[A-Za-z0-9+/_-]{200,})/i.test(value)) {
        throw new HttpsError("invalid-argument", "Describe the concern in words. Remove links or encoded content.");
    }
    return value.trim();
}

exports.submitSafetyEntry = onCall({ region, maxInstances: 10 }, async (request) => {
    const reporterId = requireUid(request);
    const fields = ["kind", "category", "description", "requestId", "reportedUserId", "matchId", "messageId"];
    if (!request.data || Object.keys(request.data).some((key) => !fields.includes(key))) {
        throw new HttpsError("invalid-argument", "Submit only the report form fields.");
    }
    const { kind, category, description: rawDescription, requestId } = request.data ?? {};
    const feedback = kind === "feedback";
    if (!["feedback", "concern", "profile", "conversation", "message"].includes(kind) ||
        !(feedback ? feedbackCategories : reportCategories).has(category)) {
        throw new HttpsError("invalid-argument", "Choose a valid category.");
    }
    const description = validateText(rawDescription, feedback || kind === "concern");
    const db = getFirestore();
    let reportedUserId = "";
    let conversationId = "";
    let messageReference = null;

    if (kind === "profile") {
        reportedUserId = validateId(request.data.reportedUserId);
        const profile = await db.collection("users").doc(reportedUserId).get();
        if (!profile.exists || reportedUserId === reporterId) {
            throw new HttpsError("invalid-argument", "Choose another VEIL account.");
        }
    }

    if (kind === "message" || kind === "conversation") {
        conversationId = validateId(request.data.matchId);
        const match = db.collection("matches").doc(conversationId);
        const senderPair = (await match.get()).data();
        if (!senderPair || ![senderPair.userA, senderPair.userB].includes(reporterId)) {
            throw new HttpsError("permission-denied", "This conversation is unavailable.");
        }
        const partner = senderPair.userA === reporterId ? senderPair.userB : senderPair.userA;
        reportedUserId = validateId(partner);
        if (kind === "message") {
            const messageId = validateId(request.data.messageId);
            const message = (await match.collection("messages").doc(messageId).get()).data();
            if (!message || message.senderUid !== partner || partner === reporterId) {
                throw new HttpsError("not-found", "This message is unavailable.");
            }
            // Keep evidence references only. Never copy message bodies, media URLs or attachments.
            messageReference = { matchId: conversationId, messageId, sentAt: Number.isSafeInteger(message.sentAt) ? message.sentAt : null };
        }
    }

    const urgent = urgentCategories.has(category);
    const now = Date.now();
    const contact = conversationId || reportedUserId;
    let deduplicationKey;
    if (messageReference) deduplicationKey = `${messageReference.matchId}/${messageReference.messageId}`;
    else if (contact) deduplicationKey = `${contact}/${Math.floor(now / 86400000)}`;
    else deduplicationKey = validateId(requestId);
    const documentId = createHash("sha256").update(JSON.stringify([reporterId, kind, category, deduplicationKey])).digest("hex");
    const entry = db.collection(feedback ? "safetyFeedback" : "safetyReports").doc(documentId);
    const rate = db.collection("safetySubmissionRates").doc(reporterId);
    await db.runTransaction(async (transaction) => {
        const [existing, rateState] = await transaction.getAll(entry, rate);
        if (existing.exists) return;
        const bucket = feedback ? "feedback" : urgent ? "urgent" : "reports";
        const previous = rateState.get(bucket);
        const windowStart = previous && now - previous.windowStart < 60 * 60 * 1000 ? previous.windowStart : now;
        const count = windowStart === previous?.windowStart ? previous.count : 0;
        if (count >= (urgent ? 30 : 10)) {
            throw new HttpsError("resource-exhausted", "Submission limit reached. Try again later or contact support.");
        }
        transaction.set(rate, { [bucket]: { windowStart, count: count + 1 } }, { merge: true });
        const common = { reporterId, category, description, createdAt: FieldValue.serverTimestamp(), status: "pending" };
        transaction.create(entry, feedback ? common : {
            ...common, kind, reportedUserId, conversationId, messageReference, priority: urgent ? "high" : "normal",
            queueRank: urgent ? 0 : 1
        });
    });
    return { id: documentId };
});

exports.reviewSafetyReport = onCall({ region, maxInstances: 5 }, async (request) => {
    const moderatorId = requireUid(request);
    if (request.auth.token.safetyModerator !== true) {
        throw new HttpsError("permission-denied", "Safety moderator access is required.");
    }
    const id = validateId(request.data?.id);
    const action = request.data?.action;
    const removeMessage = request.data?.removeMessage === true;
    if (!["reviewed", "suspend", "ban"].includes(action)) {
        throw new HttpsError("invalid-argument", "Choose a valid action.");
    }
    const note = validateText(request.data?.note, true);
    const db = getFirestore();
    const report = db.collection("safetyReports").doc(id);
    const audit = report.collection("actions").doc();
    const until = action === "suspend" ? Timestamp.fromMillis(Date.now() + 7 * 86400000) : null;

    await db.runTransaction(async (transaction) => {
        const data = (await transaction.get(report)).data();
        if (!data) throw new HttpsError("not-found", "Report unavailable.");
        if (data.status !== "pending") throw new HttpsError("failed-precondition", "This report has already been reviewed.");
        let sourceMessage = null;
        if (removeMessage) {
            if (!data.messageReference) throw new HttpsError("invalid-argument", "No source message is linked to this report.");
            const { matchId, messageId } = data.messageReference;
            sourceMessage = db.collection("matches").doc(matchId).collection("messages").doc(messageId);
        }
        if (action !== "reviewed") {
            const target = data.reportedUserId;
            if (!target || target === moderatorId) throw new HttpsError("invalid-argument", "No eligible account is linked to the report.");
            const restriction = db.collection("safetyRestrictions").doc(target);
            const previous = (await transaction.get(restriction)).data();
            if (action === "suspend" && previous?.permanent) {
                throw new HttpsError("failed-precondition", "This account already has a permanent safety restriction.");
            }
            transaction.set(restriction, { permanent: action === "ban", until });
            transaction.delete(db.collection("peopleProfiles").doc(target));
        }
        const reviewedAt = FieldValue.serverTimestamp();
        if (sourceMessage) transaction.delete(sourceMessage);
        transaction.update(report, { status: "reviewed", queueRank: 2, reviewedBy: moderatorId, reviewedAt, actionTaken: action });
        transaction.create(audit, { moderatorId, action, note, reviewedAt, until, removedMessage: removeMessage });
    });
    return { success: true };
});
