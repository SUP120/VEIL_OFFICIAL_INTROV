# VEIL group notifications

The Android app commits each message and its room preview together in Firestore. Group pushes use Firebase Cloud Messaging. The Cloudflare relay can send those pushes while the Firebase project stays on Spark. The app queues the relay call after Firestore confirms each message, including messages sent from Android's notification Reply action.

## Cloudflare setup

1. Sign in to a VEIL-owned Cloudflare account on Workers Free. Check `wrangler.toml` against the Android app registration in Firebase.
2. In the same Firebase project, enable the FCM HTTP v1 API and prepare a dedicated service account with access to read the group message, membership, device and notification preference documents and write delivery receipts. Grant the Firestore `Cloud Datastore User` and `Firebase Cloud Messaging API Admin` roles. Keep the service account credentials in the Worker secret `GROUP_PUSH_SERVICE_ACCOUNT`. The Worker uses Firebase Auth and Android App Check to authenticate callers.
3. Deploy the Worker and add the service account JSON through Wrangler's secret input. Run the commands below from the repository root with Cloudflare CLI access.

   ```powershell
   npx wrangler deploy --config cloudflare-worker/group-push/wrangler.toml
   npx wrangler secret put GROUP_PUSH_SERVICE_ACCOUNT --config cloudflare-worker/group-push/wrangler.toml
   ```

4. Check that the deployed Worker's `/health` endpoint returns `200`. Set the Android Gradle property `veilGroupPushUrl` to the Worker's complete HTTPS URL ending in `/group-message`. This URL is public configuration. Confirm the production Firestore rules include the group-only merge before distributing the Android update.
5. Use a Play internal test build so the app can present a valid Play Integrity App Check token. Sign in with two accounts, allow Android notifications and join the same group. Background one device, send from the other, tap Reply in the notification, and check that the reply appears for both accounts. Repeat for a reply-only group, a muted group, a blocked member, and an approval-only group.

Each user receives group pushes on their most recently registered device. The relay stops dispatching messages older than ten minutes. The app uses per-message receipts to suppress repeat alerts during delivery retries.

## Firebase Functions option

On Blaze, the existing trigger can be deployed with `firebase deploy --project introver-match-app --only functions:notifyGroupChatMessage`. Leave `veilGroupPushUrl` empty for that build. Messages mark which transport they use so a Worker and a deployed trigger can coexist during a rollout.

## Verification

- `node scripts/check-veil-group-push.mjs` tests the relay logic with simulated delivery.
- Run `node scripts/check-veil-groups.cjs` through `firebase emulators:exec` using project `demo-veil-groups` and the Auth and Firestore emulators.
