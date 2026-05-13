// api/create-credit-order.js
// POST /api/create-credit-order
// Creates a Cashfree order for a chat credit pack purchase (single or group).

const https = require("https");
const { getDb } = require("./_firebase");
const { Timestamp } = require("firebase-admin/firestore");

const CF_APP_ID = process.env.CASHFREE_APP_ID;
const CF_SECRET_KEY = process.env.CASHFREE_SECRET_KEY;
const CF_BASE_URL = process.env.CASHFREE_ENV === "production"
    ? "https://api.cashfree.com"
    : "https://sandbox.cashfree.com";
const CF_API_VERSION = "2023-08-01";

const PACKAGES = {
    spark: { messages: 20, basePrice: 29, label: "Spark Pack ✨" },
    glow: { messages: 60, basePrice: 49, label: "Glow Pack 🌟" },
    // "custom" handled separately using pricePerMsg
};
const PRICE_PER_CUSTOM_MSG = 0.10;
const CUSTOM_MIN_MESSAGES = 100;

// Discount by recipient count: index = count (1-4)
const GROUP_DISCOUNTS = [0, 0, 10, 15, 20];

function discountForCount(count) {
    return GROUP_DISCOUNTS[Math.min(count, 4)] || 0;
}

function calcTotal(packageId, recipientCount, customMessages) {
    let pricePerPerson;
    if (packageId === "custom") {
        const msgs = Math.max(customMessages || CUSTOM_MIN_MESSAGES, CUSTOM_MIN_MESSAGES);
        pricePerPerson = msgs * PRICE_PER_CUSTOM_MSG;
    } else {
        const pkg = PACKAGES[packageId];
        if (!pkg) return null;
        pricePerPerson = pkg.basePrice;
    }
    const rawTotal = pricePerPerson * recipientCount;
    const discount = discountForCount(recipientCount);
    const discounted = rawTotal * (1 - discount / 100);
    return Math.round(discounted * 100) / 100;
}

function messagesFor(packageId, customMessages) {
    if (packageId === "custom") {
        return Math.max(customMessages || CUSTOM_MIN_MESSAGES, CUSTOM_MIN_MESSAGES);
    }
    return PACKAGES[packageId]?.messages || 20;
}

module.exports = async function handler(req, res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    if (req.method === "OPTIONS") return res.status(200).end();
    if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

    const { buyerUserId, packageId, customMessages, recipientUserIds } = req.body || {};

    if (!buyerUserId || !packageId || !Array.isArray(recipientUserIds) || !recipientUserIds.length) {
        return res.status(400).json({ error: "buyerUserId, packageId, recipientUserIds required" });
    }
    if (!PACKAGES[packageId] && packageId !== "custom") {
        return res.status(400).json({ error: "Invalid packageId — use spark, glow, or custom" });
    }
    if (packageId === "custom" && (!customMessages || customMessages < CUSTOM_MIN_MESSAGES)) {
        return res.status(400).json({ error: `Custom pack minimum ${CUSTOM_MIN_MESSAGES} messages` });
    }

    // Buyer always included
    const recipients = [...new Set([buyerUserId, ...recipientUserIds])];
    const recipientCount = recipients.length;
    const discountPct = discountForCount(recipientCount);
    const totalAmount = calcTotal(packageId, recipientCount, customMessages);
    const msgPerPerson = messagesFor(packageId, customMessages);

    if (!totalAmount || totalAmount <= 0) {
        return res.status(400).json({ error: "Could not calculate total amount" });
    }

    try {
        const db = getDb();
        const userDoc = await db.collection("users").doc(buyerUserId).get();
        const user = userDoc.data() || {};

        // Short order ID (≤45 chars)
        const shortUid = buyerUserId.substring(0, 8).replace(/[^a-zA-Z0-9]/g, "x");
        const shortTs = String(Date.now()).slice(-9);
        const orderId = `vc_${packageId.substring(0, 3)}_${shortUid}_${shortTs}`;

        const cashfreePayload = {
            order_id: orderId,
            order_amount: totalAmount,
            order_currency: "INR",
            customer_details: {
                customer_id: buyerUserId.substring(0, 45),
                customer_name: user.alias || "Veil User",
                customer_email: user.email || "user@veil.app",
                customer_phone: user.phone || "9999999999",
            },
            order_meta: {
                return_url: `veilapp://credits?order_id={order_id}`,
                notify_url: `${process.env.BASE_URL || "https://veil-api-iota.vercel.app"}/api/webhook`,
            },
            order_tags: {
                buyerUserId,
                packageId,
                purpose: "chat_credits",
                recipientCount: String(recipientCount),
            },
        };

        const cfResp = await callCashfree("POST", "/pg/orders", cashfreePayload);

        if (!cfResp.payment_session_id) {
            console.error("Cashfree error:", JSON.stringify(cfResp));
            return res.status(500).json({ error: cfResp.message || "Cashfree order creation failed" });
        }

        // Persist to Firestore
        await db.collection("creditOrders").doc(orderId).set({
            orderId,
            buyerUserId,
            packageId,
            messagesPerPerson: msgPerPerson,
            recipientUserIds: recipients,
            discountPct,
            totalAmount,
            currency: "INR",
            status: "PENDING",
            createdAt: Timestamp.now(),
        });

        console.log(`Credit order created: ${orderId} for ${recipientCount} recipients, ₹${totalAmount}`);

        return res.status(200).json({
            orderId,
            paymentSessionId: cfResp.payment_session_id,
            totalAmount,
            discountPct,
            messagesPerPerson: msgPerPerson,
            recipientCount,
        });

    } catch (err) {
        console.error("create-credit-order error:", err);
        return res.status(500).json({ error: "Internal error" });
    }
};

function callCashfree(method, path, body) {
    return new Promise((resolve, reject) => {
        const bodyStr = body ? JSON.stringify(body) : null;
        const url = new URL(CF_BASE_URL + path);
        const opts = {
            hostname: url.hostname,
            path: url.pathname,
            method,
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
                catch { reject(new Error("Bad JSON from Cashfree: " + data.substring(0, 200))); }
            });
        });
        req.on("error", reject);
        if (bodyStr) req.write(bodyStr);
        req.end();
    });
}
