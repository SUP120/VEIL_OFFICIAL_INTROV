// Preview: node scripts/backfill-veil-people.cjs --project=YOUR_FIREBASE_PROJECT
// Apply:   node scripts/backfill-veil-people.cjs --project=YOUR_FIREBASE_PROJECT --apply
// Uses Application Default Credentials. It never rewrites a member's People settings.
const { createRequire } = require("node:module");
const { resolve } = require("node:path");
const fromFunctions = createRequire(resolve(__dirname, "../functions/package.json"));
const { initializeApp } = fromFunctions("firebase-admin/app");
const { getAuth } = fromFunctions("firebase-admin/auth");
const { FieldPath, getFirestore } = fromFunctions("firebase-admin/firestore");

const projectId = process.argv.find(arg => arg.startsWith("--project="))?.slice(10);
const apply = process.argv.includes("--apply");
if (!projectId || !/^[a-z][a-z0-9-]+$/.test(projectId)) {
    console.error("Provide --project=YOUR_FIREBASE_PROJECT. Add --apply only to write the previewed profiles.");
    process.exit(1);
}
initializeApp({ projectId });

async function migrateMember(db, uid) {
    return db.runTransaction(async transaction => {
        const memberRef = db.doc(`users/${uid}`);
        const settingsRef = db.doc(`peopleSettings/${uid}`);
        const restrictionRef = db.doc(`safetyRestrictions/${uid}`);
        const [member, preferences, restriction] = await transaction.getAll(memberRef, settingsRef, restrictionRef);
        const info = member.data();
        if (!info || !info.profileComplete || info.isReviewerAccount || info.isBanned || preferences.exists) {
            return false;
        }
        const enforcement = restriction.data();
        if (enforcement && (enforcement.permanent || enforcement.until?.toMillis() > Date.now())) {
            return false;
        }
        if (apply) {
            const alias = (typeof info.alias === "string" ? info.alias.trim().slice(0, 40) : "") || "Anonymous";
            transaction.set(settingsRef, { username: "", listed: true });
            transaction.set(db.doc(`peopleProfiles/${uid}`), { alias, searchAlias: alias.toLowerCase(), username: "" });
        }
        return true;
    });
}

async function main() {
    const db = getFirestore();
    const source = db.collection("users").where("profileComplete", "==", true).orderBy(FieldPath.documentId());
    let after;
    let eligible = 0;
    let scanned = 0;
    for (;;) {
        const page = await (after ? source.startAfter(after) : source).limit(100).get();
        if (page.empty) break;
        scanned += page.size;
        const { users } = await getAuth().getUsers(page.docs.map(doc => ({ uid: doc.id })));
        const members = users.filter(user => !user.disabled);
        for (let start = 0; start < members.length; start += 10) {
            const results = await Promise.all(members.slice(start, start + 10).map(user => migrateMember(db, user.uid)));
            eligible += results.filter(Boolean).length;
        }
        after = page.docs.at(-1);
    }
    console.log(`${projectId}: scanned ${scanned} completed profiles. ${apply ? "Added" : "Would add"} ${eligible} People entries.`);
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
