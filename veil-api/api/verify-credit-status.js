// api/verify-credit-status.js
// Checks creditOrders + Cashfree to verify credit purchase.
// Uses FieldValue.increment + set(merge:true) — safe for any user doc state.

const https = require("https");
const { getDb } = require("./_firebase");
const { Timestamp, FieldValue } = require("firebase-admin/firestore");

const CF_APP_ID = process.env.CASHFREE_APP_ID;
const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY;
const CF_BASE_URL = process.env.CASHFREE_ENV === "production"
    ? "https://api.cashfree.com"
    : "https://sandbox.cashfree.com";
const CF_API_VERSION = "2023-08-01";

const ONESIGNAL_APP_ID = process.env.ONESIGNAL_APP_ID;
const ONESIGNAL_API_KEY = process.env.ONESIGNAL_REST_API_KEY;

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

        // ── Validate order ownership ──────────────────────────────────────────
        const orderDoc = await db.collection("creditOrders").doc(orderId).get();
        if (!orderDoc.exists) {
            console.error(`creditOrders/${orderId} not found`);
            return res.status(404).json({ error: "Order not found" });
        }
        const order = orderDoc.data();
        if (order.buyerUserId !== userId) return res.status(403).json({ error: "Forbidden" });

        // ── Already confirmed — return immediately ────────────────────────────
        if (order.status === "SUCCESS") {
            console.log(`Order ${orderId} already SUCCESS, returning immediately`);
            return res.status(200).json({ orderStatus: "SUCCESS", creditGranted: true });
        }

        // ── Ask Cashfree: check order status ──────────────────────────────────
        let paymentConfirmed = false;
        let cfOrderStatus = "ACTIVE";

        try {
            const cfOrder = await callCashfree("GET", `/pg/orders/${orderId}`);
            cfOrderStatus = cfOrder.order_status || "ACTIVE";
            paymentConfirmed = cfOrderStatus === "PAID";
            console.log(`Cashfree order_status=${cfOrderStatus} for ${orderId}`);
        } catch (e) {
            console.error("Cashfree order fetch failed:", e.message);
        }

        // ── Also check payments[] (catches sandbox lag) ───────────────────────
        if (!paymentConfirmed) {
            try {
                const payments = await callCashfree("GET", `/pg/orders/${orderId}/payments`);
                const list = Array.isArray(payments) ? payments : [];
                console.log(`Cashfree payments count=${list.length}`, JSON.stringify(list.map(p => p.payment_status)));
                paymentConfirmed = list.some(p =>
                    p.payment_status === "SUCCESS" || p.payment_status === "PAID"
                );
                if (paymentConfirmed) console.log("✅ Payment confirmed via payments[] for", orderId);
            } catch (e) {
                console.error("Cashfree payments[] fetch failed:", e.message);
            }
        }

        // ── Not paid yet ──────────────────────────────────────────────────────
        if (!paymentConfirmed) {
            return res.status(200).json({ orderStatus: cfOrderStatus, creditGranted: false });
        }

        // ── Payment confirmed — grant credits to all recipients ───────────────
        console.log(`✅ Granting ${order.messagesPerPerson} credits to`, order.recipientUserIds);

        // 1. Mark order SUCCESS (separate operation so failure doesn't block credits)
        try {
            await db.collection("creditOrders").doc(orderId).update({
                status: "SUCCESS",
                paidAt: Timestamp.now(),
            });
        } catch (e) {
            console.error("Failed to mark order SUCCESS:", e.message);
            // Continue — still grant credits even if this update fails
        }

        // 2. Grant credits to each recipient individually using increment()
        //    set(merge:true) creates fields if they don't exist — never fails on missing fields
        const grantErrors = [];
        let buyerAlias = "Someone";
        try {
            const buyerDoc = await db.collection("users").doc(order.buyerUserId).get();
            buyerAlias = buyerDoc.data()?.alias || "Someone";
        } catch (_) { }

        for (const uid of order.recipientUserIds) {
            try {
                await db.collection("users").doc(uid).set({
                    chatMsgLimit: FieldValue.increment(order.messagesPerPerson),
                    chatLimitUnlocked: true,
                    lastCreditAt: Timestamp.now(),
                    creditsGranted: FieldValue.increment(order.messagesPerPerson),
                }, { merge: true });
                console.log(`✅ Granted ${order.messagesPerPerson} credits to ${uid}`);
            } catch (e) {
                const msg = `Failed to grant to ${uid}: ${e.message}`;
                console.error(msg);
                grantErrors.push(msg);
            }
        }

        // 3. Push notifications to non-buyer recipients (non-blocking)
        const nonBuyers = order.recipientUserIds.filter(uid => uid !== order.buyerUserId);
        for (const uid of nonBuyers) {
            sendCreditNotification(uid, buyerAlias, order.messagesPerPerson).catch(e =>
                console.error("Notification failed for", uid, e.message)
            );
        }

        // Return SUCCESS even if some grants failed — Cashfree confirmed payment
        return res.status(200).json({
            orderStatus: "SUCCESS",
            creditGranted: true,
            ...(grantErrors.length ? { warnings: grantErrors } : {}),
        });

    } catch (err) {
        console.error("verify-credit-status top-level error:", err.message, err.stack);
        return res.status(500).json({ error: "Internal error: " + err.message });
    }
};

// ── OneSignal push notification ───────────────────────────────────────────────
async function sendCreditNotification(recipientUserId, buyerAlias, messages) {
    if (!ONESIGNAL_APP_ID || !ONESIGNAL_API_KEY) return;
    const payload = JSON.stringify({
        app_id: ONESIGNAL_APP_ID,
        filters: [{ field: "external_user_id", value: recipientUserId }],
        headings: { en: "🎁 Your conversation just got more space!" },
        contents: { en: `${buyerAlias} gave you ${messages} more messages. Go say something real 💬` },
        data: { type: "credit_gifted", recipientUserId, messages },
    });
    return new Promise((resolve, reject) => {
        const opts = {
            hostname: "onesignal.com", path: "/api/v1/notifications", method: "POST",
            headers: {
                "Authorization": `Basic ${ONESIGNAL_API_KEY}`,
                "Content-Type": "application/json",
                "Content-Length": Buffer.byteLength(payload),
            },
        };
        const req = https.request(opts, (r) => {
            let d = ""; r.on("data", c => { d += c; }); r.on("end", () => resolve(d));
        });
        req.on("error", reject);
        req.write(payload); req.end();
    });
}

// ── Cashfree API helper ───────────────────────────────────────────────────────
function callCashfree(method, path, body) {
    return new Promise((resolve, reject) => {
        const bodyStr = body ? JSON.stringify(body) : null;
        const url = new URL(CF_BASE_URL + path);
        const opts = {
            hostname: url.hostname, path: url.pathname, method,
            headers: {
                "x-api-version": CF_API_VERSION,
                "x-client-id": CF_APP_ID,
                "x-client-secret": CF_SECRET_KEY,
                "Content-Type": "application/json",
                ...(bodyStr ? { "Content-Length": Buffer.byteLength(bodyStr) } : {}),
            },
        };
        const req = https.request(opts, (r) => {
            let data = "";
            r.on("data", c => { data += c; });
            r.on("end", () => {
                try { resolve(JSON.parse(data)); }
                catch { reject(new Error("Bad JSON: " + data.substring(0, 300))); }
            });
        });
        req.on("error", reject);
        if (bodyStr) req.write(bodyStr);
        req.end();
    });
}
