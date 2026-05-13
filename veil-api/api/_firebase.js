// api/_firebase.js
// Shared Firebase Admin initializer — called once, cached for reuse across invocations.

const { initializeApp, getApps, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

let db = null;

function getDb() {
    if (db) return db;

    // Firebase Admin uses a Service Account JSON.
    // The JSON content is stored as an env var: FIREBASE_SERVICE_ACCOUNT
    // (paste the entire JSON as a single-line string in Vercel env vars)
    if (!getApps().length) {
        const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || "{}");
        initializeApp({ credential: cert(serviceAccount) });
    }

    db = getFirestore();
    return db;
}

module.exports = { getDb };
