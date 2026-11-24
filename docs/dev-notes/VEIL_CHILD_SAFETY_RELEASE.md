# VEIL child safety release

This guide covers Firebase setup, testing, the public standards page and the Play Console resubmission for VEIL 1.17.4. Complete the release gates before certifying the declaration.

## Firebase setup

1. Use a staging Firebase project and disposable accounts first. Compare its Firestore rules with `firestore.rules`. Before changing production, compare the complete rules in **Firebase Console → Firestore Database → Rules** with the checked-in file and merge any live-only changes. This avoids replacing unrelated production rules.
2. In the repository root, run `firebase login` and confirm the project ID in **Project settings → General**. Cloud Functions deployment requires a Firebase project on the Blaze plan. Use Node.js 22 and install the locked dependencies with `npm ci` in `functions/` if required. Deploy the two safety functions and the merged Firestore rules to staging first. Repeat with the production project ID after staging passes and before uploading the new Android release.

   ```powershell
   firebase deploy --project <confirmed-project-id> --only 'functions:submitSafetyEntry,functions:reviewSafetyReport,firestore:rules'
   ```

   Check **Firebase Console → Functions** for `submitSafetyEntry` and `reviewSafetyReport` in `asia-south1` and **Firestore Database → Rules** for the active rules. These screens use the existing authenticated Firebase session. The new collections are created on first use.
3. In **Authentication → Users**, locate the designated moderator's existing verified account and copy its Firebase UID. Set the `safetyModerator` custom claim from an authorized Admin SDK environment. Firebase Console does not provide an editor for custom claims. Run the example from `functions/` with trusted Application Default Credentials for the intended project. Keep credentials on the administrative machine.

   ```javascript
   const { initializeApp, applicationDefault } = require('firebase-admin/app');
   const { getAuth } = require('firebase-admin/auth');

   initializeApp({ credential: applicationDefault(), projectId: 'CONFIRMED_PROJECT_ID' });
   const auth = getAuth();
   const uid = 'VERIFIED_MODERATOR_UID';
   auth.getUser(uid).then((user) => auth.setCustomUserClaims(uid, {
       ...user.customClaims,
       safetyModerator: true,
   })).catch((error) => { console.error(error.message); process.exitCode = 1; });
   ```

   Sign into VEIL as that account and open **Profile → Help & Safety → Safety moderation**. The legacy admin dashboard also links to the protected queue. To remove access, remove `safetyModerator` from the account's existing claims and revoke its refresh tokens through the Admin SDK. Allow already-issued ID tokens to expire and keep moderator devices under team control.

[Firebase Rules deployment](https://firebase.google.com/docs/rules/manage-deploy) · [Firebase custom claims](https://firebase.google.com/docs/auth/admin/custom-claims) · [Cloud Functions runtime support](https://cloud.google.com/functions/docs/runtime-support)

## Test before rollout

Run the automated checks from the repository root. Use JDK 17 for the Android build. Use a Java version supported by the installed Firebase CLI for the emulator and Node with the Functions dependencies installed. The emulator script uses the `demo-veil-safety` project and synthetic data.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:bundleRelease
firebase emulators:exec --only 'auth,firestore,functions' --project demo-veil-safety 'node scripts/check-veil-safety.cjs'
```

If the Gradle daemon runs out of memory during `bundleRelease`, shut down the Android emulator and retry with one worker. This command keeps release optimization and lint enabled.

```powershell
.\gradlew.bat :app:bundleRelease --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=512m -XX:ActiveProcessorCount=4 -XX:+UseSerialGC -Dfile.encoding=UTF-8'
```

Use two ordinary Android test accounts and a moderator account on staging. Use synthetic text and harmless media for every test.

| Flow | Steps and expected result |
| --- | --- |
| Report user | From a feed user's profile, open its safety menu and choose **Report User**. Check that the categories appear, details are optional, and success follows submission. Confirm a second submission for the same account and category does not create a duplicate case. |
| Report conversation | Open a direct chat, open its safety menu and choose **Report Conversation**. Confirm the queue shows the linked conversation. |
| Report message | Open a direct chat and tap the flag next to a received message. Test recent and paginated messages, including a harmless image. Check that the queue shows the exact conversation, message reference and sender. Check that the submitted document contains no copy of the message or media URL. |
| Submit feedback | Open **Profile → Help & Safety → Send Feedback**. Choose a category, enter a description and submit. Confirm that VEIL shows **Feedback received** and the entry appears in the moderator's **Feedback** tab. Test blank descriptions, a URL and a disconnected network. The form should show an error or prevent submission and keep the input for a retry. |
| Child safety | From **Profile → Help & Safety → Report a Safety Concern**, submit a synthetic example under **Child Safety Concern**. Confirm **High priority** and verify that the form offers no attachment action. |
| Block | From the profile, chat safety menu or DM inbox, block a test account. On both accounts, verify that the direct chat becomes unavailable and both directions reject new messages, likes and contact requests. Start a fresh chat between two unblocked accounts and verify messages, notifications and the inbox still work. Blocking covers direct contact. Shared public group and voice room participation need separate verification before making platform-wide blocking claims. |
| Moderator access | Verify the moderation menu is absent for ordinary accounts. With the authorized account, open pending reports, inspect account and message references, record a decision and check the **History** tab and case audit. Verify a second decision on the same case fails. Test the 7-day suspension and permanent ban with disposable accounts. Confirm the restriction screen appears and that protected writes are denied. |
| Remove a message | On the synthetic report, select **Remove the source message from VEIL chat** and record a reason. Verify the Firestore message disappears from both Android accounts and the case history records the removal. The action removes the chat record. External files and storage copies require the separate handling below. |

## Child safety case handling

Assign coverage for the high-priority queue and make sure the designated contact receives and monitors incoming reports. Record who owns each case and check the queue regularly. The queue stores only the report text and reference metadata. Use text notes for the minimum information necessary and keep notes free of originals, links to suspected content and sensitive personal details.

For suspected CSAM, escalate to the designated child safety contact. The contact should promptly restrict access to the content, arrange removal from VEIL and any original media host, determine any legally required preservation and reporting, and make the required report to the applicable regional or national authority through its official channel. Follow the authority's handling instructions. Record timestamps, actions and the external case reference when available. Before launch, verify the applicable jurisdiction, reporting authority, deadlines and preservation requirements with qualified advice.

For a reported DM with an attachment, an authorized operator should use the case's conversation and message IDs to identify the original host and asset identifier from existing metadata before removing the chat record. Use the provider's restricted takedown tools without opening or downloading the media. Apply the required preservation instructions and arrange removal and cache invalidation as applicable. The moderation queue removes the Firestore message. Legacy feed content, externally hosted attachments and backup copies require separate authorized handling.

[Google Play child safety standards guidance](https://support.google.com/googleplay/android-developer/answer/14747720)

## Publish the standards page

1. Check the contact section in `website/child-safety/index.html`. It names Supriyam Kumar as the VEIL Child Safety Point of Contact and lists `solynlabs@gmail.com`. Verify that the mailbox can receive and respond to child-safety notices before publishing the page or certifying the declaration.
2. Use the existing website's hosting workflow to publish that file at `/child-safety/`. The `website/` directory has its own Git repository. Commit the page and footer link there as part of the approved website deployment. A commit to the Android repository alone does not publish or carry the website files. If the domain uses a different source, deploy `child-safety/index.html` through that host. A static host can serve the page without a backend.
3. Open the published page at `https://veil-app.in/child-safety/` in a private browser with no sign-in. Verify it loads on mobile and desktop, names VEIL and explicitly prohibits CSAE and CSAM. Confirm the published contact information and try the page from **Profile → Help & Safety → Child Safety Standards**. If a different hostname is used, update the URL in `app/src/main/java/com/ns/findyourintroverfriend/report/SafetyCenter.kt`, rebuild the app and use the final working URL in Play Console.

## Google Play resubmission

In Play Console, select VEIL and open **Policy and programs → App content → Child Safety Standards**. Use these entries once the corresponding checks pass.

| Declaration item | What to enter or verify |
| --- | --- |
| Published child safety standards | Enter `https://veil-app.in/child-safety/` if it is the final public URL and loads without sign-in. It must identify VEIL and explicitly prohibit CSAE. |
| In-app feedback | Certify only after confirming a Play test build submits successfully from **Profile → Help & Safety → Send Feedback** and **Report a Safety Concern**. Both submit inside the app. |
| CSAM response | Certify only after the team has verified the priority queue, removal path, assigned moderator and the documented escalation procedure. The queues and audits do not themselves satisfy reporting obligations. |
| Applicable laws | Certify after the designated contact has verified and implemented the applicable reporting and handling requirements. |
| Child safety contact | Enter **Supriyam Kumar**, **VEIL Child Safety Point of Contact**, and **`solynlabs@gmail.com`**. |

Upload the signed AAB through an internal or closed test track first and run the staging checklist on the Play-installed build. Confirm build code 34 is unused and higher than the code on the track you want to replace. If it is already used, set a higher code in `app/build.gradle.kts` and rebuild. After the production backend, policy page and contact are ready, create or edit the release on the affected track, upload the tested bundle, save the declaration and send the pending changes for review from **Publishing overview**. Provide reviewer sign-in instructions if Play Console requests them. Include the in-app feedback and report navigation paths in the review information where available.

Reviewer instructions after verification. “Sign in and open Profile → Help & Safety. Send Feedback and Report a Safety Concern open forms that submit inside VEIL. For a user report, open a member profile and select Report User from its menu. The flag beside a received direct message opens Report Message.”

[Google Play child safety declaration guidance](https://support.google.com/googleplay/android-developer/answer/14747720)

### Play Billing warning

The signed VEIL 1.17.4 bundle, version code 34, declares `com.google.android.play.billingclient.version` as `8.0.0` in its packaged base manifest. That satisfies the Billing Library minimum for the August 31, 2026 enforcement date. If Play Console still flags billing after the next bundle is uploaded, check **Policy status** for the affected version codes and compare them against the active releases on your Play tracks.

[Google Play Billing Library deprecation schedule](https://developer.android.com/google/play/billing/deprecation-faq.html)

## Changed source files

Paths are relative to the VEIL workspace. The files contain the complete implementation.

```text
app/build.gradle.kts
app/src/main/java/com/ns/findyourintroverfriend/MainActivity.kt
app/src/main/java/com/ns/findyourintroverfriend/admin/AdminDashboardScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/feed/FeedScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/feed/FeedUserProfileScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/home/VeilHomeScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/navigation/VeilNavGraph.kt
app/src/main/java/com/ns/findyourintroverfriend/navigation/VeilRoute.kt
app/src/main/java/com/ns/findyourintroverfriend/report/SafetyCenter.kt
app/src/main/java/com/ns/findyourintroverfriend/report/SafetyModerationScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/swipe/ConnectionRoomScreen.kt
app/src/main/java/com/ns/findyourintroverfriend/swipe/MatchesViewModel.kt
app/src/main/java/com/ns/findyourintroverfriend/swipe/SwipeProfile.kt
docs/dev-notes/VEIL_CHILD_SAFETY_RELEASE.md
firebase.json
firestore.rules
functions/index.js
functions/package.json
functions/package-lock.json
functions/safety.js
scripts/check-veil-safety.cjs
website/child-safety/index.html
website/index.html
```
