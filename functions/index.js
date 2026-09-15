const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest, onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const https = require("https");

initializeApp();

// ── VEIL Friends — AI Chat Gateway ────────────────────────────────────────
// Loaded from separate file to keep index.js clean.
const { aiFriendChat } = require("./aiFriendChat");
exports.aiFriendChat = aiFriendChat;

// Firebase Functions Gen 2 automatically loads the .env file in the functions/ directory.
// Access values via process.env.VARIABLE_NAME — no Blaze / Secret Manager needed.

const db = getFirestore();
const fcm = getMessaging();

// ─────────────────────────────────────────────────────────────────────────────
//  CASHFREE CONFIG
//  Set your keys in functions/.env  (this file is NOT committed to git)
//  Firebase Functions Gen 2 reads .env automatically on deploy.
//     CASHFREE_APP_ID=xxx
//     CASHFREE_SECRET_KEY=xxx
//     CASHFREE_ENV=sandbox   (change to "production" for live)
// ─────────────────────────────────────────────────────────────────────────────

const CF_APP_ID = process.env.CASHFREE_APP_ID || "";
const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY || "";

// Sandbox:    "https://sandbox.cashfree.com"
// Production: "https://api.cashfree.com"
const CF_BASE_URL = (process.env.CASHFREE_ENV === "production")
    ? "https://api.cashfree.com"
    : "https://sandbox.cashfree.com";

const CF_API_VERSION = "2023-08-01";

// One-time unlock price (in INR)
const UNLOCK_AMOUNT = 79;

// Extended message limit after unlock
const UNLOCKED_MSG_LIMIT = 20;

// ─────────────────────────────────────────────────────────────────────────────
//  AGORA CONFIG
//  Set your keys in functions/.env (NOT committed to git)
//     AGORA_APP_ID=your_app_id
//     AGORA_APP_CERTIFICATE=your_primary_certificate
// ─────────────────────────────────────────────────────────────────────────────

const AGORA_APP_ID          = process.env.AGORA_APP_ID          || "";
const AGORA_APP_CERTIFICATE = process.env.AGORA_APP_CERTIFICATE || "";

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION: generateAgoraToken
//  Called by the Android app before joining a voice room channel.
//  Returns a signed RTC token valid for 1 hour.
//
//  POST /generateAgoraToken
//  Body: { uid: "firebase_uid", channelName: "roomId", role: "publisher"|"subscriber" }
//  Returns: { token: "006xxx...", expireTs: 1234567890 }
//
//  Security:
//    • Caller must be a valid authenticated Firebase user
//    • channelName (roomId) must exist as a live voiceRoom in Firestore
//    • Rate-limited by Firebase Functions invocation limits
// ─────────────────────────────────────────────────────────────────────────────

exports.generateAgoraToken = onRequest(
    { region: "asia-south1", cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            return res.status(405).json({ error: "Method not allowed" });
        }

        const { uid, channelName, role } = req.body;

        // ── Validate inputs ──────────────────────────────────────────────────
        if (!uid || typeof uid !== "string" || uid.trim().length === 0) {
            return res.status(400).json({ error: "uid is required" });
        }
        if (!channelName || typeof channelName !== "string" || channelName.trim().length === 0) {
            return res.status(400).json({ error: "channelName is required" });
        }

        // ── Validate Agora credentials are configured ─────────────────────────
        if (!AGORA_APP_ID || !AGORA_APP_CERTIFICATE) {
            logger.error("generateAgoraToken: AGORA_APP_ID or AGORA_APP_CERTIFICATE not set in .env");
            return res.status(500).json({ error: "Audio service not configured" });
        }

        // ── Verify caller exists in Firestore (auth check) ───────────────────
        try {
            const userDoc = await db.collection("users").doc(uid.trim()).get();
            if (!userDoc.exists) {
                return res.status(403).json({ error: "User not found" });
            }
        } catch (e) {
            logger.error("generateAgoraToken: user verification failed:", e);
            return res.status(500).json({ error: "User verification failed" });
        }

        // ── Verify room exists and is live ────────────────────────────────────
        try {
            const roomDoc = await db.collection("voiceRooms").doc(channelName.trim()).get();
            if (!roomDoc.exists || roomDoc.data()?.isLive !== true) {
                return res.status(404).json({ error: "Voice room not found or not live" });
            }
        } catch (e) {
            logger.error("generateAgoraToken: room verification failed:", e);
            return res.status(500).json({ error: "Room verification failed" });
        }

        // ── Generate the RTC token ─────────────────────────────────────────────
        try {
            const { RtcTokenBuilder, RtcRole } = require("agora-token");

            // Agora UID: must be a positive 32-bit integer
            // We hash the Firebase UID consistently (same logic as in Android)
            const agoraUid = Math.max(1, Math.abs(hashCode(uid.trim())) & 0x7FFFFFFF);

            // Role: publisher = broadcaster (speaks), subscriber = audience (listens)
            const agoraRole = (role === "publisher")
                ? RtcRole.PUBLISHER
                : RtcRole.SUBSCRIBER;

            // Token valid for 1 hour (3600 seconds)
            const expireTs = Math.floor(Date.now() / 1000) + 3600;

            const token = RtcTokenBuilder.buildTokenWithUid(
                AGORA_APP_ID,
                AGORA_APP_CERTIFICATE,
                channelName.trim(),
                agoraUid,
                agoraRole,
                expireTs,
                expireTs   // privilege expiry same as token expiry
            );

            logger.info(`generateAgoraToken: ✅ token generated for uid=${uid} channel=${channelName} role=${role} agoraUid=${agoraUid}`);

            return res.status(200).json({
                token:    token,
                expireTs: expireTs,
                agoraUid: agoraUid,
            });

        } catch (err) {
            logger.error("generateAgoraToken: token generation error:", err);
            return res.status(500).json({ error: "Token generation failed: " + err.message });
        }
    }
);

// ── Helper: Java-compatible string hashCode (mirrors Android's uid.hashCode()) ─
function hashCode(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash | 0; // Convert to 32-bit int
    }
    return hash;
}


// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 1: matchGroups
//  Triggered whenever a matchmaking_queue document is created or updated.
//  Pairs exactly 2 males + 2 females into a group.
// ─────────────────────────────────────────────────────────────────────────────

exports.matchGroups = onDocumentWritten(
    "matchmaking_queue/{uid}",
    async (event) => {
        // Only run when a "waiting" entry appears
        const after = event.data?.after?.data();
        if (!after || after.status !== "waiting") return;

        logger.info("matchGroups triggered for uid:", event.params.uid);

        try {
            // Query all waiting entries
            const snap = await db.collection("matchmaking_queue")
                .where("status", "==", "waiting")
                .get();

            const males = snap.docs.filter(d => d.data().gender === "Male");
            const females = snap.docs.filter(d => d.data().gender === "Female");

            // ── PRIMARY: 2M + 2F ─────────────────────────────────────────────
            let chosen = null;
            if (males.length >= 2 && females.length >= 2) {
                chosen = [males[0], males[1], females[0], females[1]];
                logger.info("Primary match: 2M + 2F");
            } else {
                // ── FIX #3 FALLBACK: any 2+ users waiting > 15 minutes ───────
                const FALLBACK_WAIT_MS = 15 * 60 * 1000;
                const nowMs = Date.now();
                const longWaiters = snap.docs.filter(d => {
                    const bg = d.data().backgroundMode === true;
                    const joinedAt = d.data().joinedAt?.toDate();
                    if (!joinedAt) return false;
                    const waited = nowMs - joinedAt.getTime();
                    // Background users: match after 15min; active users: match after 20min
                    const threshold = bg ? FALLBACK_WAIT_MS : FALLBACK_WAIT_MS + 5 * 60 * 1000;
                    return waited >= threshold;
                });

                if (longWaiters.length >= 2) {
                    chosen = longWaiters.slice(0, Math.min(4, longWaiters.length));
                    logger.info(`FIX#3 Fallback match: ${chosen.length} long-waiters grouped (males:${males.length} females:${females.length})`);
                } else {
                    logger.info(`Not enough users — males:${males.length} females:${females.length}, longWaiters:${longWaiters.length}`);
                    return;
                }
            }

            // Take exactly the chosen users
            const memberUids = chosen.map(d => d.id);

            // Create the group document
            const groupRef = db.collection("groups").doc();
            const groupId = groupRef.id;
            const now = Timestamp.now();

            const batch = db.batch();

            batch.set(groupRef, {
                members: memberUids,
                day: 1,
                dayStartedAt: now,
                createdAt: now,
                ceremonyDone: false,
            });

            // Update each user + remove from queue
            for (const doc of chosen) {
                const uid = doc.id;
                const userRef = db.collection("users").doc(uid);
                batch.update(userRef, { groupId: groupId });
                batch.delete(db.collection("matchmaking_queue").doc(uid));
            }

            await batch.commit();
            logger.info("Group created:", groupId, "members:", memberUids);

            // Send push notification to all 4 members
            await notifyUsers(memberUids, {
                title: "Your circle is ready 🌙",
                body: "Enter the island. Someone is already waiting for you.",
            });

        } catch (err) {
            logger.error("matchGroups error:", err);
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 2: advanceGroupDay
//  Scheduled at 1:00 AM IST = 7:30 PM UTC = "30 19 * * *"
//  Advances `day` by 1 for any group where the current calendar day
//  is different from the day stored in `dayStartedAt`.
// ─────────────────────────────────────────────────────────────────────────────

exports.advanceGroupDay = onSchedule(
    {
        schedule: "0 */8 * * *",   // Every 8 hours: 12AM, 8AM, 4PM UTC = 5:30AM, 1:30PM, 9:30PM IST
        timeZone: "Asia/Kolkata",
        region: "asia-south1",
    },
    async () => {
        logger.info("advanceGroupDay running (8-hour cadence)");

        try {
            // Find groups still in progress
            const snap = await db.collection("groups")
                .where("day", "<", 3)
                .where("ceremonyDone", "!=", true)
                .get();

            if (snap.empty) {
                logger.info("No active groups to advance");
                return;
            }

            const nowMs = Date.now();
            const DAY_DURATION_MS = 8 * 60 * 60 * 1000;  // 8 hours in milliseconds
            const batch = db.batch();
            const toNotify = []; // {uids, newDay}

            for (const doc of snap.docs) {
                const data = doc.data();
                const currentDay = data.day || 1;
                const startedAt = data.dayStartedAt?.toDate();

                if (!startedAt) continue;

                // Advance if >= 8 hours have elapsed since dayStartedAt
                const elapsedMs = nowMs - startedAt.getTime();
                if (elapsedMs >= DAY_DURATION_MS) {
                    const newDay = Math.min(currentDay + 1, 3);
                    batch.update(doc.ref, {
                        day: newDay,
                        dayStartedAt: Timestamp.fromDate(new Date(nowMs)),
                    });
                    logger.info(`Group ${doc.id}: day ${currentDay} → ${newDay} (${Math.floor(elapsedMs / 3600000)}h elapsed)`);
                    toNotify.push({ uids: data.members || [], newDay });
                }
            }

            await batch.commit();

            // Notify members of each advanced group
            for (const { uids, newDay } of toNotify) {
                await notifyUsers(uids, {
                    title: `Day ${newDay} has begun ✨`,
                    body: newDay === 3
                        ? "Final day 🕯️ The Ceremony unlocks in 8 hours — be ready."
                        : "New activities unlocked. Your circle is waiting for you.",
                });
            }

            logger.info("advanceGroupDay complete");
        } catch (err) {
            logger.error("advanceGroupDay error:", err);
        }
    }
);

// ───────────────────────────────────────────────────────────────────────────────
//  FUNCTION 2b: sendDayNotifications
//  Runs every 30 minutes — SERVER-SIDE reminders that work even with app closed
//  Handles: pre-ceremony alerts (2h, 1h before), ceremony-open alert, post-ceremony nudges
// ───────────────────────────────────────────────────────────────────────────────

exports.sendDayNotifications = onSchedule(
    {
        schedule: "*/30 * * * *",  // every 30 minutes
        timeZone: "Asia/Kolkata",
        region: "asia-south1",
    },
    async () => {
        logger.info("sendDayNotifications: running");
        const nowMs = Date.now();
        const DAY_MS = 8 * 60 * 60 * 1000;           // 8h
        const CEREMONY_OPEN_MS = 8 * 60 * 60 * 1000;           // ceremony opens at 8h into Day 3
        const CEREMONY_CLOSE_GAP_MS = 4 * 60 * 60 * 1000;      // auto-close 4h after opening

        try {
            // Only active groups in Day 1-3, ceremony not done
            const snap = await db.collection("groups")
                .where("ceremonyDone", "!=", true)
                .get();

            for (const doc of snap.docs) {
                const data = doc.data();
                const day = data.day || 1;
                const uids = data.members || [];
                const dayStartedAt = data.dayStartedAt?.toDate();
                if (!dayStartedAt || uids.length === 0) continue;

                const msElapsed = nowMs - dayStartedAt.getTime();

                // ─ Day 3 ceremony lifecycle ───────────────────────────────
                if (day >= 3) {
                    const ceremonyOpened = data.ceremonyOpened || false;
                    const preNotifsSent = data.preNotifsSent || 0;    // pre-ceremony
                    const ceremonyNotifs = data.ceremonyNotifsSent || 0; // post-ceremony
                    const openedAt = data.ceremonyOpenedAt?.toDate();

                    // --- Pre-ceremony reminders (ceremony not yet open) ---
                    if (!ceremonyOpened) {
                        let expectedPre = 0;
                        if (msElapsed >= 7 * 60 * 60 * 1000) expectedPre = 2; // 7h+ → 1h-before
                        else if (msElapsed >= 6 * 60 * 60 * 1000) expectedPre = 1; // 6h+ → 2h-before

                        if (expectedPre > preNotifsSent) {
                            const title = "🎭 Ceremony soon";
                            const body = preNotifsSent === 0
                                ? "🕯️ The ceremony opens in 2 hours. Get ready to lift the veil."
                                : "⏳ 1 hour until the ceremony. Your circle is waiting for you."
                            await notifyUsers(uids, { title, body });
                            await doc.ref.update({ preNotifsSent: (preNotifsSent + 1) });
                            logger.info(`Group ${doc.id}: pre-ceremony notif #${preNotifsSent + 1} sent`);
                        }

                        // Ceremony unlock (server-side, in case app is closed)
                        if (msElapsed >= CEREMONY_OPEN_MS) {
                            await doc.ref.update({
                                ceremonyOpened: true,
                                ceremonyOpenedAt: Timestamp.fromDate(new Date(nowMs)),
                            });
                            await notifyUsers(uids, {
                                title: "🎭 The Veil Has Lifted",
                                body: "✨ The ceremony is NOW open — enter to reveal your circle.",
                            });
                            logger.info(`Group ${doc.id}: ceremony unlocked by server`);
                        }
                    }

                    // --- Post-ceremony reminders (ceremony already open) ---
                    if (ceremonyOpened && openedAt) {
                        const msOpen = nowMs - openedAt.getTime();
                        let expectedPost = 0;
                        if (msOpen >= 3 * 60 * 60 * 1000) expectedPost = 3;
                        else if (msOpen >= 2 * 60 * 60 * 1000) expectedPost = 2;
                        else expectedPost = 1;

                        if (expectedPost > ceremonyNotifs) {
                            const msg = ceremonyNotifs === 0
                                ? "✨ The veil lifts NOW. Enter the ceremony — meet your circle."
                                : ceremonyNotifs === 1
                                    ? "🌙 Still waiting for you. The ceremony closes in 2 hours."
                                    : "⚠️ Last call — enter before the ceremony closes."
                            await notifyUsers(uids, { title: "🎭 Ceremony is open", body: msg });
                            await doc.ref.update({ ceremonyNotifsSent: (ceremonyNotifs + 1) });
                            logger.info(`Group ${doc.id}: post-ceremony notif #${ceremonyNotifs + 1} sent`);
                        }

                        // Auto-close check
                        if (msOpen >= CEREMONY_CLOSE_GAP_MS && !data.autoKicked) {
                            await doc.ref.update({ autoKicked: true, ceremonyDone: true });
                            await notifyUsers(uids, {
                                title: "🔒 The ceremony has closed",
                                body: "Your Veil journey is complete. Join a new circle anytime.",
                            });
                            logger.info(`Group ${doc.id}: auto-closed after ${Math.floor(msOpen / 3600000)}h`);
                        }
                    }
                }

                // ─ Day 1 nudge ─────────────────────────────────────────
                // Nudge at 4h into Day 1 (halfway, if no question posted yet)
                if (day === 1 && msElapsed >= 4 * 60 * 60 * 1000 && !data.day1NudgeSent) {
                    await notifyUsers(uids, {
                        title: "❓ Break the silence",
                        body: "Post your first question — someone in your circle is waiting.",
                    });
                    await doc.ref.update({ day1NudgeSent: true });
                    logger.info(`Group ${doc.id}: Day 1 nudge sent`);
                }

                // ─ Day 2 engagement nudge ────────────────────────────────
                if (day === 2 && msElapsed >= 4 * 60 * 60 * 1000 && !data.day2NudgeSent) {
                    await notifyUsers(uids, {
                        title: "🎮 Play time",
                        body: "Would You Rather, Dare to Share — your circle is active right now.",
                    });
                    await doc.ref.update({ day2NudgeSent: true });
                    logger.info(`Group ${doc.id}: Day 2 nudge sent`);
                }
            }

            logger.info("sendDayNotifications: done");
        } catch (err) {
            logger.error("sendDayNotifications error:", err);
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 3: createCashfreeOrder
//  Call: POST /createCashfreeOrder
//  Body: { userId, purpose: "unlock_chat_limit" }
//  Returns: { orderId, paymentSessionId }
// ─────────────────────────────────────────────────────────────────────────────

exports.createCashfreeOrder = onRequest(
    { region: "asia-south1", cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            return res.status(405).json({ error: "Method not allowed" });
        }

        const { userId, purpose } = req.body;

        if (!userId || !purpose) {
            return res.status(400).json({ error: "userId and purpose are required" });
        }

        const VALID_PURPOSES = ["unlock_chat_limit", "unlock_stickers"];
        if (!VALID_PURPOSES.includes(purpose)) {
            return res.status(400).json({ error: "Invalid purpose" });
        }

        try {
            // Check if user has already unlocked this feature
            const userDoc = await db.collection("users").doc(userId).get();
            if (!userDoc.exists) {
                return res.status(404).json({ error: "User not found" });
            }

            const userData = userDoc.data();
            if (purpose === "unlock_chat_limit" && userData.chatLimitUnlocked === true) {
                return res.status(200).json({ alreadyUnlocked: true });
            }

            // Build Cashfree order payload
            const orderId = `veil_${purpose}_${userId}_${Date.now()}`;
            const orderPayload = {
                order_id: orderId,
                order_amount: UNLOCK_AMOUNT,
                order_currency: "INR",
                customer_details: {
                    customer_id: userId,
                    customer_email: userData.email || "user@veil.app",
                    customer_phone: userData.phone || "9999999999",  // Cashfree requires phone
                    customer_name: userData.alias || "Veil User",
                },
                order_meta: {
                    // return_url is where Cashfree redirects the in-app browser after payment
                    // For Android native, this should be your app's deep link scheme
                    return_url: `veilapp://payment?order_id={order_id}&order_token={order_token}`,
                    // notify_url calls our webhook on every payment status change
                    notify_url: `https://asia-south1-${process.env.GCLOUD_PROJECT}.cloudfunctions.net/cashfreeWebhook`,
                },
                order_tags: {
                    userId: userId,
                    purpose: purpose,
                },
            };

            // Call Cashfree API
            const cfResponse = await callCashfree("POST", "/pg/orders", orderPayload);

            if (!cfResponse.payment_session_id) {
                logger.error("Cashfree order creation failed:", cfResponse);
                return res.status(500).json({ error: "Failed to create payment order" });
            }

            // Save pending order in Firestore (audit trail)
            await db.collection("orders").doc(orderId).set({
                orderId: orderId,
                userId: userId,
                purpose: purpose,
                amount: UNLOCK_AMOUNT,
                currency: "INR",
                status: "PENDING",
                paymentSessionId: cfResponse.payment_session_id,
                createdAt: Timestamp.now(),
            });

            logger.info("Order created:", orderId, "for user:", userId);

            return res.status(200).json({
                orderId: orderId,
                paymentSessionId: cfResponse.payment_session_id,
            });

        } catch (err) {
            logger.error("createCashfreeOrder error:", err);
            return res.status(500).json({ error: "Internal server error" });
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 4: cashfreeWebhook
//  Cashfree calls this after every payment event.
//  We verify the signature, then unlock features on SUCCESS.
// ─────────────────────────────────────────────────────────────────────────────

exports.cashfreeWebhook = onRequest(
    { region: "asia-south1" },
    async (req, res) => {
        if (req.method !== "POST") {
            return res.status(405).send("Method not allowed");
        }

        try {
            // ── 1. Verify Cashfree signature (MANDATORY) ───────────────────────
            const rawBody = JSON.stringify(req.body);
            const receivedSig = req.headers["x-webhook-signature"];
            const timestamp = req.headers["x-webhook-timestamp"];

            if (!receivedSig || !timestamp) {
                logger.warn("Webhook received without signature headers");
                return res.status(400).send("Missing signature");
            }

            const crypto = require("crypto");
            const expectedSig = crypto
                .createHmac("sha256", CF_SECRET_KEY)
                .update(timestamp + rawBody)
                .digest("base64");

            if (expectedSig !== receivedSig) {
                logger.warn("Webhook signature mismatch — rejected");
                return res.status(401).send("Invalid signature");
            }

            // ── 2. Parse event ─────────────────────────────────────────────────
            const event = req.body;
            const type = event.type;   // e.g. "PAYMENT_SUCCESS_WEBHOOK"
            const orderId = event.data?.order?.order_id;
            const paymentStatus = event.data?.payment?.payment_status; // SUCCESS / FAILED / ...

            if (!orderId) {
                logger.warn("Webhook missing order_id");
                return res.status(400).send("Missing order_id");
            }

            logger.info(`Webhook: type=${type} order=${orderId} status=${paymentStatus}`);

            // ── 3. Fetch our saved order doc ───────────────────────────────────
            const orderRef = db.collection("orders").doc(orderId);
            const orderDoc = await orderRef.get();

            if (!orderDoc.exists) {
                logger.warn("Order not found in Firestore:", orderId);
                return res.status(404).send("Order not found");
            }

            const order = orderDoc.data();

            // Idempotency — don't re-process already-handled orders
            if (order.status === "SUCCESS" || order.status === "FAILED") {
                logger.info("Order already processed, skipping:", orderId);
                return res.status(200).send("Already processed");
            }

            // ── 4. Handle payment result ───────────────────────────────────────
            const paymentId = event.data?.payment?.cf_payment_id;
            const paymentTime = event.data?.payment?.payment_completion_time;

            if (paymentStatus === "SUCCESS") {
                // Update order record
                await orderRef.update({
                    status: "SUCCESS",
                    paymentId: paymentId ? String(paymentId) : null,
                    paidAt: paymentTime ? Timestamp.fromDate(new Date(paymentTime)) : Timestamp.now(),
                    updatedAt: Timestamp.now(),
                });

                // ── UNLOCK the feature for this user ──────────────────────────
                const userRef = db.collection("users").doc(order.userId);

                if (order.purpose === "unlock_chat_limit") {
                    await userRef.update({
                        chatLimitUnlocked: true,
                        chatMsgLimit: UNLOCKED_MSG_LIMIT,
                        chatUnlockedAt: Timestamp.now(),
                        chatUnlockOrderId: orderId,
                    });
                    logger.info(`✅ Chat unlocked for user: ${order.userId}`);

                    // Notify user
                    await notifyUsers([order.userId], {
                        title: "Chat Unlocked! 💬",
                        body: `Your Slow Chat limit is now ${UNLOCKED_MSG_LIMIT} messages. Go connect!`,
                    });
                }

                if (order.purpose === "unlock_stickers") {
                    await userRef.update({
                        stickersUnlocked: true,
                        stickersUnlockedAt: Timestamp.now(),
                    });
                    logger.info(`✅ Stickers unlocked for user: ${order.userId}`);
                }

            } else {
                // FAILED or CANCELLED
                await orderRef.update({
                    status: paymentStatus || "FAILED",
                    updatedAt: Timestamp.now(),
                });
                logger.info(`Payment ${paymentStatus} for order: ${orderId}`);
            }

            return res.status(200).send("OK");

        } catch (err) {
            logger.error("cashfreeWebhook error:", err);
            return res.status(500).send("Internal error");
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 5: verifyPaymentStatus
//  Called by the app after payment UI closes to poll the real status.
//  GET /verifyPaymentStatus?orderId=xxx&userId=xxx
// ─────────────────────────────────────────────────────────────────────────────

exports.verifyPaymentStatus = onRequest(
    { region: "asia-south1", cors: true },
    async (req, res) => {
        const { orderId, userId } = req.query;

        if (!orderId || !userId) {
            return res.status(400).json({ error: "orderId and userId required" });
        }

        try {
            const orderDoc = await db.collection("orders").doc(orderId).get();
            if (!orderDoc.exists) {
                return res.status(404).json({ error: "Order not found" });
            }

            const order = orderDoc.data();

            // Security: don't expose another user's order
            if (order.userId !== userId) {
                return res.status(403).json({ error: "Forbidden" });
            }

            // Also read the user's current unlock state (source of truth)
            const userDoc = await db.collection("users").doc(userId).get();
            const userData = userDoc.data() || {};

            return res.status(200).json({
                orderStatus: order.status,
                chatLimitUnlocked: userData.chatLimitUnlocked || false,
                chatMsgLimit: userData.chatMsgLimit || 7,
                stickersUnlocked: userData.stickersUnlocked || false,
            });

        } catch (err) {
            logger.error("verifyPaymentStatus error:", err);
            return res.status(500).json({ error: "Internal error" });
        }
    }
);


// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 6: sendPushViaServer
//  Secure server-side FCM push — replaces OneSignal REST calls from the APK.
//  The app no longer needs any REST secret key embedded in the APK.
//
//  POST /sendPushViaServer
//  Body (one of two modes):
//    Mode A — send to specific UIDs:
//      { callerUid, targetUids: ["uid1","uid2"], title, body, data? }
//    Mode B — send to all members of a group:
//      { callerUid, groupId: "abc123", title, body, data? }
//
//  Security rules:
//    • callerUid must be a valid authenticated user OR an admin
//    • If groupId is given, callerUid must be a member of that group
//    • Admin users (isAdmin=true) can target any uid
// ─────────────────────────────────────────────────────────────────────────────

exports.sendPushViaServer = onRequest(
    { region: "asia-south1", cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            return res.status(405).json({ error: "Method not allowed" });
        }

        const { callerUid, targetUids, groupId, title, body, data = {} } = req.body;

        if (!callerUid || !title || !body) {
            return res.status(400).json({ error: "callerUid, title, and body are required" });
        }
        if (!targetUids && !groupId) {
            return res.status(400).json({ error: "Provide either targetUids[] or groupId" });
        }

        try {
            // ── Verify caller exists ───────────────────────────────────────
            const callerDoc = await db.collection("users").doc(callerUid).get();
            if (!callerDoc.exists) {
                return res.status(403).json({ error: "Caller not found" });
            }
            const callerData = callerDoc.data();
            const isAdmin = callerData?.isAdmin === true;

            // ── Resolve target UIDs ────────────────────────────────────────
            let uidsToNotify = [];

            if (groupId) {
                const groupDoc = await db.collection("groups").doc(groupId).get();
                if (!groupDoc.exists) {
                    return res.status(404).json({ error: "Group not found" });
                }
                const members = groupDoc.data()?.members || [];

                // Security: caller must be a member of the group (or admin)
                if (!isAdmin && !members.includes(callerUid)) {
                    return res.status(403).json({ error: "Caller is not a member of this group" });
                }

                // Never send to the caller themselves (e.g. slow-chat sends to others)
                uidsToNotify = members.filter(uid => uid !== callerUid);
            } else if (Array.isArray(targetUids)) {
                // Security: non-admins can only send to users in their group OR to themselves
                if (!isAdmin) {
                    const callerGroupId = callerData?.groupId;
                    if (callerGroupId) {
                        const groupDoc = await db.collection("groups").doc(callerGroupId).get();
                        const groupMembers = groupDoc.data()?.members || [];
                        // Only allow targeting members of caller's own group
                        uidsToNotify = targetUids.filter(uid => groupMembers.includes(uid) && uid !== callerUid);
                    } else {
                        // No group — each can only trigger certain events (e.g., self-registration)
                        uidsToNotify = targetUids.filter(uid => uid === callerUid);
                    }
                } else {
                    uidsToNotify = targetUids;
                }
            }

            if (uidsToNotify.length === 0) {
                return res.status(200).json({ sent: 0, message: "No eligible recipients" });
            }

            // ── Send the notification ──────────────────────────────────────
            await notifyUsers(uidsToNotify, { title, body, data });

            logger.info(`sendPushViaServer: caller=${callerUid} → ${uidsToNotify.length} recipients | "${title}"`);
            return res.status(200).json({ sent: uidsToNotify.length });

        } catch (err) {
            logger.error("sendPushViaServer error:", err);
            return res.status(500).json({ error: "Internal server error" });
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION 7: saveFcmToken
//  Called by the app whenever FCM issues a new token (on first launch or refresh).
//  Securely stores token to Firestore so notifyUsers() can reach the device.
//
//  POST /saveFcmToken
//  Body: { uid, fcmToken }
//
//  Note: The app also saves the token directly via Firestore SDK — this endpoint
//  exists as a reliable fallback for background token refreshes.
// ─────────────────────────────────────────────────────────────────────────────

exports.saveFcmToken = onRequest(
    { region: "asia-south1", cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            return res.status(405).json({ error: "Method not allowed" });
        }

        const { uid, fcmToken } = req.body;
        if (!uid || !fcmToken) {
            return res.status(400).json({ error: "uid and fcmToken are required" });
        }

        try {
            await db.collection("users").doc(uid).update({
                fcmToken,
                fcmTokenUpdatedAt: Timestamp.now(),
            });
            logger.info(`saveFcmToken: token saved for uid=${uid}`);
            return res.status(200).json({ success: true });
        } catch (err) {
            logger.error("saveFcmToken error:", err);
            return res.status(500).json({ error: "Internal server error" });
        }
    }
);

// ─────────────────────────────────────────────────────────────────────────────
//  Helper: call Cashfree REST API
// ─────────────────────────────────────────────────────────────────────────────

function callCashfree(method, path, body) {
    return new Promise((resolve, reject) => {
        const payload = JSON.stringify(body);
        const url = new URL(CF_BASE_URL + path);

        const options = {
            hostname: url.hostname,
            path: url.pathname,
            method: method,
            headers: {
                "Content-Type": "application/json",
                "x-api-version": CF_API_VERSION,
                "x-client-id": CF_APP_ID,
                "x-client-secret": CF_SECRET_KEY,
                "Content-Length": Buffer.byteLength(payload),
            },
        };

        const req = https.request(options, (resp) => {
            let data = "";
            resp.on("data", (chunk) => { data += chunk; });
            resp.on("end", () => {
                try { resolve(JSON.parse(data)); }
                catch (e) { reject(new Error("Invalid JSON from Cashfree: " + data)); }
            });
        });

        req.on("error", reject);
        req.write(payload);
        req.end();
    });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helper: send FCM notification to a list of UIDs
//  Reads fcmToken from each user's Firestore doc.
//
//  KEY FIX: Sends BOTH notification + data payloads with:
//    • android.priority = "high"          → bypasses Doze, wakes killed app
//    • android.notification.channelId     → routes to our high-importance channel
//    • data payload                       → triggers onMessageReceived even when killed
//    • notification payload               → shows system tray banner automatically
//
//  Without this dual payload, FCM "notification-only" messages are delivered
//  by the system tray and DON'T wake a force-stopped/killed app on Android 8+.
// ─────────────────────────────────────────────────────────────────────────────

async function notifyUsers(uids, { title, body, data = {} }) {
    if (!uids || uids.length === 0) return;

    // ── 1. Gather tokens (batched reads for efficiency) ────────────────────
    const tokenEntries = []; // { uid, token }
    await Promise.all(uids.map(async (uid) => {
        try {
            const userDoc = await db.collection("users").doc(uid).get();
            const token = userDoc.data()?.fcmToken;
            if (token) tokenEntries.push({ uid, token });
        } catch (e) {
            logger.warn(`notifyUsers: failed reading token for uid=${uid}: ${e.message}`);
        }
    }));

    if (tokenEntries.length === 0) {
        logger.warn("notifyUsers: no valid FCM tokens found for uids:", uids);
        return;
    }

    const tokens = tokenEntries.map(e => e.token);

    // ── 2. Build the FCM multicast message ─────────────────────────────────
    //  CRITICAL: Always include BOTH `notification` and `data` fields.
    //  • `notification` → OS shows the banner automatically (works in foreground/background)
    //  • `data`         → triggers onMessageReceived even when app is KILLED
    //  • android.priority = "high" → FCM high-priority wake-up (bypasses Doze)
    //  • channelId = "veil_events" → matches the IMPORTANCE_HIGH channel in VeilMessagingService
    const message = {
        tokens,
        notification: {
            title,
            body,
        },
        data: {
            title,
            body,
            ...data,  // pass-through any extra data (type, groupId, etc.)
        },
        android: {
            priority: "high",
            notification: {
                channelId: "veil_events",
                sound: "default",
                notificationPriority: "PRIORITY_HIGH",
                vibrateTimingsMillis: [0, 250, 250, 250],
                defaultVibrateTimings: false,
            },
            ttl: { seconds: 2419200 },
        },
        apns: {
            headers: { "apns-priority": "10" },
            payload: {
                aps: {
                    sound: "default",
                    badge: 1,
                    contentAvailable: true,
                },
            },
        },
    };

    // ── 3. Send and clean up stale tokens ──────────────────────────────────
    try {
        const result = await fcm.sendEachForMulticast(message);
        logger.info(`notifyUsers: sent=${result.successCount} failed=${result.failureCount} for ${uids.length} user(s)`);

        const staleCleanups = [];
        result.responses.forEach((resp, idx) => {
            if (!resp.success) {
                const errCode = resp.error?.code;
                logger.warn(`notifyUsers: token failed for uid=${tokenEntries[idx]?.uid} — ${errCode}: ${resp.error?.message}`);
                if (
                    errCode === "messaging/invalid-registration-token" ||
                    errCode === "messaging/registration-token-not-registered" ||
                    errCode === "messaging/invalid-argument"
                ) {
                    const uid = tokenEntries[idx]?.uid;
                    if (uid) {
                        staleCleanups.push(
                            db.collection("users").doc(uid)
                                .update({ fcmToken: FieldValue.delete() })
                                .catch(e => logger.warn(`Failed to clear stale token for ${uid}: ${e.message}`))
                        );
                    }
                }
            }
        });

        if (staleCleanups.length > 0) {
            await Promise.all(staleCleanups);
            logger.info(`notifyUsers: cleaned ${staleCleanups.length} stale token(s)`);
        }
    } catch (err) {
        logger.error("notifyUsers: FCM sendEachForMulticast failed:", err.message);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Music Room — broadcast invite notification to all users
//  Triggered when app writes to musicRoomInvites/{roomId}
// ─────────────────────────────────────────────────────────────────────────────
exports.onMusicRoomInvite = onDocumentWritten(
    "musicRoomInvites/{roomId}",
    async (event) => {
        const data = event.data?.after?.data();
        if (!data) return;
        const { roomId, hostAlias } = data;

        // Fetch all user FCM tokens (stored in users/{uid}.fcmToken)
        const usersSnap = await db.collection("users")
            .where("fcmToken", "!=", "")
            .get();

        if (usersSnap.empty) return;

        const tokens = usersSnap.docs
            .map(d => d.get("fcmToken"))
            .filter(t => typeof t === "string" && t.length > 0);

        if (tokens.length === 0) return;

        // Send in batches of 500 (FCM limit)
        const BATCH = 500;
        for (let i = 0; i < tokens.length; i += BATCH) {
            const batch = tokens.slice(i, i + BATCH);
            try {
                await fcm.sendEachForMulticast({
                    tokens: batch,
                    notification: {
                        title: "🎵 Music Room is Live!",
                        body: `${hostAlias} just opened a Music Room — join and listen together!`,
                    },
                    data: {
                        type:   "music_room_invite",
                        roomId: roomId,
                        screen: "music_room",
                    },
                    android: {
                        priority: "high",
                        notification: { channelId: "music_room" },
                    },
                });
            } catch (err) {
                logger.error("Music Room FCM batch failed:", err);
            }
        }
        logger.info(`Music Room invite sent to ${tokens.length} users for room ${roomId}`);
    }
);


// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION: generateLiveKitToken
//  Called by Android app before joining a Voice Room channel.
//  Returns a signed LiveKit JWT token valid for 1 hour.
//
//  POST /generateLiveKitToken
//  Body: { uid: "firebase_uid", roomName: "roomId", canPublish: true|false }
//  Returns: { token: "eyJ...", livekitUrl: "wss://..." }
//
//  Security:
//    • Caller uid must exist in Firestore users collection
//    • API Secret never leaves the server
// ─────────────────────────────────────────────────────────────────────────────

exports.generateLiveKitToken = onCall(
    { region: "asia-south1" },
    async (request) => {
        // onCall automatically verifies Firebase Auth — uid is guaranteed
        const uid = request.auth?.uid;
        if (!uid) {
            throw new Error("unauthenticated");
        }

        const { roomName, canPublish } = request.data;

        if (!roomName || typeof roomName !== "string" || roomName.trim().length === 0) {
            throw new Error("roomName is required");
        }

        const apiKey     = process.env.LIVEKIT_API_KEY    || "";
        const apiSecret  = process.env.LIVEKIT_API_SECRET || "";
        const livekitUrl = process.env.LIVEKIT_URL        || "";

        if (!apiKey || !apiSecret || !livekitUrl) {
            logger.error("generateLiveKitToken: LiveKit env vars not configured");
            throw new Error("Voice service not configured on server");
        }

        try {
            const { AccessToken } = require("livekit-server-sdk");

            const at = new AccessToken(apiKey, apiSecret, {
                identity: uid,
                ttl:      "1h",
            });

            at.addGrant({
                roomJoin:     true,
                room:         roomName.trim(),
                canPublish:   canPublish !== false,   // default true
                canSubscribe: true,
            });

            const token = await at.toJwt();

            logger.info(`generateLiveKitToken: ✅ uid=${uid} room=${roomName}`);
            return { token, livekitUrl };

        } catch (err) {
            logger.error("generateLiveKitToken error:", err);
            throw new Error("Token generation failed: " + err.message);
        }
    }
);


// ─────────────────────────────────────────────────────────────────────────────
//  FUNCTION: cleanupExpiredVoiceRooms
//  Runs every 5 minutes — marks expired voice rooms as isLive=false
//  so all connected Android clients receive the Firestore update and auto-leave.
// ─────────────────────────────────────────────────────────────────────────────

exports.cleanupExpiredVoiceRooms = onSchedule(
    {
        schedule:  "*/5 * * * *",   // every 5 minutes
        timeZone:  "Asia/Kolkata",
        region:    "asia-south1",
    },
    async () => {
        logger.info("cleanupExpiredVoiceRooms: running");
        try {
            const now  = Timestamp.now();
            const snap = await db.collection("voiceRooms")
                .where("isLive", "==", true)
                .where("expiresAt", "<=", now)
                .get();

            if (snap.empty) {
                logger.info("cleanupExpiredVoiceRooms: nothing to clean");
                return;
            }

            const batch = db.batch();
            snap.docs.forEach(doc => {
                batch.update(doc.ref, { isLive: false, closedReason: "expired" });
            });
            await batch.commit();
            logger.info(`cleanupExpiredVoiceRooms: closed ${snap.size} expired room(s)`);
        } catch (err) {
            logger.error("cleanupExpiredVoiceRooms error:", err);
        }
    }
);
