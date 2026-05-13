// api/verify-status.js
// GET /api/verify-status?orderId=xxx&userId=xxx
// Checks BOTH the order status AND the payments list from Cashfree.
// If either confirms payment SUCCESS, unlocks immediately in Firestore.

const https = require("https");
const { getDb } = require("./_firebase");
const { Timestamp } = require("firebase-admin/firestore");

const CF_APP_ID = process.env.CASHFREE_APP_ID;
const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY;
const CF_BASE_URL = process.env.CASHFREE_ENV === "production"
    ? "https://api.cashfree.com"
    : "https://sandbox.cashfree.com";
const CF_API_VERSION = "2023-08-01";
const UNLOCKED_LIMIT = 20;

module.exports = async function handler(req, res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
    if (req.method === "OPTIONS") return res.status(200).end();
    if (req.method !== "GET") return res.status(405).json({ error: "Method not allowed" });

    const { orderId, userId } = req.query;
    if (!orderId || !userId) {
        return res.status(400).json({ error: "orderId and userId required" });
    }

    try {
        const db = getDb();

        // ── 1. Validate order ownership ───────────────────────────────────────
        const orderDoc = await db.collection("orders").doc(orderId).get();
        if (!orderDoc.exists) return res.status(404).json({ error: "Order not found" });

        const order = orderDoc.data();
        if (order.userId !== userId) return res.status(403).json({ error: "Forbidden" });

        // ── 2. Already confirmed in Firestore — short-circuit ─────────────────
        if (order.status === "SUCCESS") {
            const userSnap = await db.collection("users").doc(userId).get();
            const u = userSnap.data() || {};
            return res.status(200).json({
                orderStatus: "SUCCESS",
                chatLimitUnlocked: true,
                chatMsgLimit: u.chatMsgLimit || UNLOCKED_LIMIT,
                stickersUnlocked: u.stickersUnlocked || false,
            });
        }

        // ── 3. Ask Cashfree: order status ─────────────────────────────────────
        let cfStatus = "ACTIVE";
        try {
            const cfOrder = await callCashfree("GET", `/pg/orders/${orderId}`);
            cfStatus = cfOrder.order_status || "ACTIVE";
            console.log(`Cashfree order_status=${cfStatus} for ${orderId}`);
        } catch (e) {
            console.error("Cashfree order fetch error:", e.message);
        }

        // ── 4. Also check payments list — catches sandbox where order_status
        //       may lag behind individual payment_status ─────────────────────
        let paymentConfirmed = cfStatus === "PAID";
        if (!paymentConfirmed) {
            try {
                const payments = await callCashfree("GET", `/pg/orders/${orderId}/payments`);
                const list = Array.isArray(payments) ? payments : [];
                console.log(`Cashfree payments count=${list.length} for ${orderId}`);
                paymentConfirmed = list.some(p =>
                    p.payment_status === "SUCCESS" || p.payment_status === "PAID"
                );
                if (paymentConfirmed) {
                    console.log("✅ Payment confirmed via payments[] endpoint");
                }
            } catch (e) {
                console.error("Cashfree payments fetch error:", e.message);
            }
        }

        // ── 5. If confirmed — unlock in Firestore & return SUCCESS ────────────
        if (paymentConfirmed) {
            try {
                const batch = db.batch();
                batch.update(db.collection("orders").doc(orderId), {
                    status: "SUCCESS",
                    paidAt: Timestamp.now(),
                });
                batch.update(db.collection("users").doc(userId), {
                    chatLimitUnlocked: true,
                    chatMsgLimit: UNLOCKED_LIMIT,
                    unlockedAt: Timestamp.now(),
                    unlockedVia: "verify-status-direct",
                });
                await batch.commit();
                console.log("✅ Unlocked:", userId, orderId);
            } catch (e) {
                console.error("Firestore unlock error:", e.message);
            }

            return res.status(200).json({
                orderStatus: "SUCCESS",
                chatLimitUnlocked: true,
                chatMsgLimit: UNLOCKED_LIMIT,
                stickersUnlocked: false,
            });
        }

        // ── 6. Not paid yet ───────────────────────────────────────────────────
        const userSnap = await db.collection("users").doc(userId).get();
        const u = userSnap.data() || {};
        return res.status(200).json({
            orderStatus: cfStatus,
            chatLimitUnlocked: u.chatLimitUnlocked || false,
            chatMsgLimit: u.chatMsgLimit || 7,
            stickersUnlocked: u.stickersUnlocked || false,
        });

    } catch (err) {
        console.error("verify-status error:", err);
        return res.status(500).json({ error: "Internal error" });
    }
};

function callCashfree(method, path) {
    return new Promise((resolve, reject) => {
        const url = new URL(CF_BASE_URL + path);
        const options = {
            hostname: url.hostname,
            path: url.pathname,
            method,
            headers: {
                "x-api-version": CF_API_VERSION,
                "x-client-id": CF_APP_ID,
                "x-client-secret": CF_SECRET_KEY,
                "Content-Type": "application/json",
            },
        };
        const req = https.request(options, (r) => {
            let data = "";
            r.on("data", c => { data += c; });
            r.on("end", () => {
                try { resolve(JSON.parse(data)); }
                catch { reject(new Error("Bad JSON from Cashfree: " + data.substring(0, 200))); }
            });
        });
        req.on("error", reject);
        req.end();
    });
}
