// api/create-order.js
// POST /api/create-order
// Body: { userId, purpose }
// Returns: { orderId, paymentSessionId } or { alreadyUnlocked: true }

const https = require("https");
const { getDb } = require("./_firebase");
const { Timestamp } = require("firebase-admin/firestore");

const CF_APP_ID = process.env.CASHFREE_APP_ID;
const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY;
const CF_BASE_URL = process.env.CASHFREE_ENV === "production"
    ? "https://api.cashfree.com"
    : "https://sandbox.cashfree.com";
const CF_API_VERSION = "2023-08-01";
const UNLOCK_AMOUNT = 1;   // ₹1 for production test — change to 79 before real launch

module.exports = async function handler(req, res) {
    // CORS headers
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");
    if (req.method === "OPTIONS") return res.status(200).end();
    if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

    const { userId, purpose } = req.body;
    if (!userId || !purpose) return res.status(400).json({ error: "userId and purpose required" });

    const VALID = ["unlock_chat_limit", "unlock_stickers"];
    if (!VALID.includes(purpose)) return res.status(400).json({ error: "Invalid purpose" });

    try {
        const db = getDb();
        const userDoc = await db.collection("users").doc(userId).get();
        if (!userDoc.exists) return res.status(404).json({ error: "User not found" });

        const user = userDoc.data();

        // Already unlocked?
        if (purpose === "unlock_chat_limit" && user.chatLimitUnlocked === true) {
            return res.status(200).json({ alreadyUnlocked: true });
        }

        // Cashfree order_id: max 50 chars, alphanumeric + underscore only
        // veil_unlock_chat_limit_<28 char uid>_<13 digit ts> = ~65 chars → OVER LIMIT
        // Fixed: vl_ucl_<10 chars>_<10 digits> = ~28 chars safely
        const shortPurpose = purpose === "unlock_chat_limit" ? "ucl" : "us";
        const shortUid = userId.substring(0, 10).replace(/[^a-zA-Z0-9]/g, "x");
        const shortTs = String(Date.now()).slice(-10);
        const orderId = `vl_${shortPurpose}_${shortUid}_${shortTs}`;
        const payload = {
            order_id: orderId,
            order_amount: UNLOCK_AMOUNT,
            order_currency: "INR",
            customer_details: {
                customer_id: userId,
                customer_email: user.email || "user@veil.app",
                customer_phone: user.phone || "9999999999",
                customer_name: user.alias || "Veil User",
            },
            order_meta: {
                // Deep link back into the app after payment
                return_url: `veilapp://payment?order_id={order_id}`,
                // ⚠️ Set BASE_URL env var in Vercel → https://veil-api-iota.vercel.app
                notify_url: `${process.env.BASE_URL || "https://veil-api-iota.vercel.app"}/api/webhook`,
            },
            order_tags: { userId, purpose },
        };

        const cfResp = await callCashfree("POST", "/pg/orders", payload);
        if (!cfResp.payment_session_id) {
            console.error("Cashfree error:", cfResp);
            return res.status(500).json({ error: "Cashfree order creation failed" });
        }

        // Save pending order in Firestore
        await db.collection("orders").doc(orderId).set({
            orderId,
            userId,
            purpose,
            amount: UNLOCK_AMOUNT,
            currency: "INR",
            status: "PENDING",
            paymentSessionId: cfResp.payment_session_id,
            createdAt: Timestamp.now(),
        });

        console.log("Order created:", orderId);
        return res.status(200).json({
            orderId,
            paymentSessionId: cfResp.payment_session_id,
        });

    } catch (err) {
        console.error("create-order error:", err);
        return res.status(500).json({ error: "Internal server error" });
    }
};

function callCashfree(method, path, body) {
    return new Promise((resolve, reject) => {
        const payload = JSON.stringify(body);
        const url = new URL(CF_BASE_URL + path);
        const options = {
            hostname: url.hostname,
            path: url.pathname,
            method,
            headers: {
                "Content-Type": "application/json",
                "x-api-version": CF_API_VERSION,
                "x-client-id": CF_APP_ID,
                "x-client-secret": CF_SECRET_KEY,
                "Content-Length": Buffer.byteLength(payload),
            },
        };
        const req = https.request(options, (r) => {
            let data = "";
            r.on("data", (c) => { data += c; });
            r.on("end", () => {
                try { resolve(JSON.parse(data)); }
                catch (e) { reject(new Error("Bad JSON from Cashfree: " + data)); }
            });
        });
        req.on("error", reject);
        req.write(payload);
        req.end();
    });
}
