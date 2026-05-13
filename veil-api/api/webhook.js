// api/webhook.js
// POST /api/webhook
// Called by Cashfree on every payment event.
// Verifies signature → unlocks features in Firestore on SUCCESS.

const crypto = require("crypto");
const { getDb } = require("./_firebase");
const { Timestamp } = require("firebase-admin/firestore");

const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY;
const UNLOCKED_LIMIT = 20;

module.exports = async function handler(req, res) {
    if (req.method !== "POST") return res.status(405).send("Method not allowed");

    try {
        // ── 1. Verify Cashfree signature ──────────────────────────────────────
        const rawBody = JSON.stringify(req.body);
        const receivedSig = req.headers["x-webhook-signature"];
        const timestamp = req.headers["x-webhook-timestamp"];

        if (!receivedSig || !timestamp) {
            console.warn("Webhook missing signature headers");
            return res.status(400).send("Missing signature");
        }

        const expectedSig = crypto
            .createHmac("sha256", CF_SECRET_KEY)
            .update(timestamp + rawBody)
            .digest("base64");

        if (expectedSig !== receivedSig) {
            console.warn("Webhook signature mismatch — rejected");
            return res.status(401).send("Invalid signature");
        }

        // ── 2. Parse event ────────────────────────────────────────────────────
        const event = req.body;
        const orderId = event.data?.order?.order_id;
        const paymentStatus = event.data?.payment?.payment_status; // SUCCESS / FAILED / ...

        if (!orderId) {
            console.warn("Webhook missing order_id");
            return res.status(400).send("Missing order_id");
        }

        console.log(`Webhook: order=${orderId} status=${paymentStatus}`);

        // ── 3. Fetch order from Firestore ─────────────────────────────────────
        const db = getDb();
        const orderRef = db.collection("orders").doc(orderId);
        const orderDoc = await orderRef.get();

        if (!orderDoc.exists) {
            console.warn("Order not found:", orderId);
            return res.status(404).send("Order not found");
        }

        const order = orderDoc.data();

        // Idempotency — skip already-processed orders
        if (order.status === "SUCCESS" || order.status === "FAILED") {
            console.log("Already processed, skipping:", orderId);
            return res.status(200).send("Already processed");
        }

        // ── 4. Handle result ──────────────────────────────────────────────────
        const paymentId = event.data?.payment?.cf_payment_id;
        const paymentTime = event.data?.payment?.payment_completion_time;

        if (paymentStatus === "SUCCESS") {
            // Update order record
            await orderRef.update({
                status: "SUCCESS",
                paymentId: paymentId ? String(paymentId) : null,
                paidAt: paymentTime
                    ? Timestamp.fromDate(new Date(paymentTime))
                    : Timestamp.now(),
                updatedAt: Timestamp.now(),
            });

            // Unlock the feature in user's doc
            const userRef = db.collection("users").doc(order.userId);

            if (order.purpose === "unlock_chat_limit") {
                await userRef.update({
                    chatLimitUnlocked: true,
                    chatMsgLimit: UNLOCKED_LIMIT,
                    chatUnlockedAt: Timestamp.now(),
                    chatUnlockOrderId: orderId,
                });
                console.log("✅ Chat unlocked for user:", order.userId);
            }

            if (order.purpose === "unlock_stickers") {
                await userRef.update({
                    stickersUnlocked: true,
                    stickersUnlockedAt: Timestamp.now(),
                });
                console.log("✅ Stickers unlocked for user:", order.userId);
            }

        } else {
            // FAILED, CANCELLED, etc.
            await orderRef.update({
                status: paymentStatus || "FAILED",
                updatedAt: Timestamp.now(),
            });
            console.log(`Payment ${paymentStatus} for order:`, orderId);
        }

        return res.status(200).send("OK");

    } catch (err) {
        console.error("webhook error:", err);
        return res.status(500).send("Internal error");
    }
};
