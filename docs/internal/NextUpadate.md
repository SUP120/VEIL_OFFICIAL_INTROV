## VEIL MULTIPLAYER 3D WORLD INTEGRATION

## 1. YOUR ROLE

Act as a senior Unity multiplayer engineer, Unity 6 developer, Android integration engineer, and technical mentor.

You are working with me on VEIL, an existing Android application built using Kotlin and Firebase.

I am a beginner in networking, multiplayer architecture, Unity project configuration, and Android integration. I can operate Unity Editor and Android Studio, but I need you to handle technical analysis and implementation carefully.

Your job is to inspect the existing projects, understand the current architecture, make safe incremental changes, test what you can, and explain exactly what I need to do manually.

Do not assume that I understand servers, networking, Unity components, Android Gradle configuration, or multiplayer synchronization.

Give exact instructions with file paths, Unity hierarchy names, menu locations, and terminal commands whenever required.

## 2. MY ACTUAL PROJECT CONTEXT

### Existing Android application

Project name: VEIL

Platform: Android

Native technology:
- Kotlin
- Android Studio
- Firebase

VEIL is a social application focused on helping people connect and spend time together.

I want to add an exciting multiplayer 3D social world inside VEIL, where friends can meet, explore, interact, and eventually play multiplayer mini-games.

My long-term goal is to make the 3D world feel like a natural part of VEIL, not like an unrelated external game.

### Existing Unity project

I downloaded the official Unity Technologies sample:

GitHub repository:
https://github.com/Unity-Technologies/U6-First-Person-Multiplayer

This is NOT Unity Boss Room. Do not assume the project is Boss Room.

My Unity Editor version is:

Unity 6.3 LTS — 6000.3.5f2

The project is already imported into Unity Editor.

The Unity project window shows these scene assets under Assets/Scenes:

- SampleScene
- Multiplayer
- Singleplayer
- VEIL(MAIN)Game

The currently visible hierarchy in my screenshot contains objects such as:

- Essentials
- Environment
- Spawn Positions
- Network Manager
- Game Manager
- Item

The Assets folder also contains folders such as:

- Cinemachine
- Input
- Materials
- Prefabs
- Samples
- Scenes
- Scripts
- Settings
- Sprites
- Terrain
- Textures
- TutorialInfo
- UI Toolkit

A prefab asset beginning with DefaultNetworkPre... is also visible.

These are observations from the Editor. Verify the actual project files and hierarchy before relying on them.

### Current status

I have already opened the multiplayer sample and verified that two players can connect successfully.

This is critical: the existing multiplayer implementation is already working.

DO NOT destroy, replace, or casually rewrite the existing multiplayer system.

The project has been copied/backed up before modifications.

## 3. FINAL PRODUCT VISION

Build a multiplayer social world called VEIL World.

Users should eventually be able to:

1. Open VEIL on Android.
2. Enter the 3D social world from within VEIL.
3. Join a session with friends.
4. See other connected players.
5. Walk around in first-person view.
6. Explore a peaceful, stylized social environment.
7. Talk to friends through text chat.
8. Eventually use proximity voice chat.
9. Interact with locations and objects.
10. Join separate multiplayer mini-games in the future.

The first release does NOT need every feature.

Prioritize a reliable, small, working multiplayer prototype over ambitious but unfinished features.

## 4. NON-NEGOTIABLE RULES

Before changing anything, follow these rules.

### Rule A — Inspect before editing

First inspect the complete project structure.

Identify:
- Unity scenes and their purposes.
- The current multiplayer scene.
- The existing player prefab.
- The NetworkManager configuration.
- Network transport.
- Player spawning.
- Input system and controls.
- Camera and Cinemachine setup.
- Authentication.
- Relay connection flow.
- Lobby or session flow.
- Voice chat/Vivox integration, if present.
- Existing networking scripts.
- Build Profiles and platform settings.
- Packages and Unity project version.

Read relevant scripts before changing them.

Do not infer architecture from object names alone.

### Rule B — Preserve multiplayer

The existing two-player connection must remain functional.

Do not:
- Delete the NetworkManager.
- Replace the current network transport without a clear reason.
- Remove network components from the player prefab.
- Remove NetworkObject or NetworkBehaviour components.
- Break ownership or authority logic.
- Disable player spawning.
- Replace working session-join logic with a custom implementation unnecessarily.
- Remove existing Relay, authentication, lobby, or voice integrations.
- Replace the working multiplayer scene without a migration plan.

If any change may affect multiplayer, explain the risk and test the original flow again.

### Rule C — Make backups

Before modifying a script, prefab, scene, package, or build configuration:

1. Confirm that the existing backup is available.
2. Identify the exact files or assets being modified.
3. Prefer reversible changes.
4. Never overwrite the backup.
5. Do not delete original scenes or prefabs.

Use version control if available, but do not require it as a condition for proceeding.

### Rule D — Do not make assumptions

If the project contains an existing implementation for a feature, reuse it whenever appropriate.

Do not create duplicate systems for:
- Multiplayer connection.
- Player spawning.
- Player movement.
- Camera control.
- Session management.
- Input handling.
- Authentication.
- Voice communication.

If the implementation cannot be determined, stop and explain what information is missing.

### Rule E — Beginner-friendly communication

After each phase, report:
- What you inspected.
- What you changed.
- The exact file paths.
- Why the changes were necessary.
- Which components or packages are involved.
- What I need to click in Unity.
- How I can test the result.
- Any errors or remaining work.

Never say a feature works unless it has actually been tested or clearly identify it as unverified.

## 5. TARGET ARCHITECTURE

Use the following separation of responsibilities.

### Unity responsibilities

Unity should handle:
- 3D rendering.
- First-person camera.
- Player movement.
- Real-time multiplayer.
- Player spawning.
- World interactions.
- Multiplayer mini-games.
- Real-time object synchronization.
- Optional proximity voice integration.

Reuse the downloaded sample's existing networking stack wherever practical.

### VEIL Android responsibilities

The existing Kotlin application should continue handling:
- VEIL login and account screens.
- Existing Firebase integration.
- User profiles.
- Friend lists and friend requests.
- Social features.
- App navigation.
- Permissions and Android lifecycle.
- Launching and returning from the Unity experience.

Do not replace Firebase with a new backend just because Unity is being introduced.

### Integration responsibilities

Investigate Unity as a Library (UaaL) to embed Unity into the existing Android application.

Official Unity documentation:
https://docs.unity.com/en-us/engine/6000.3/manual/platform-specific/android/developing/unityasa-library

Determine whether the current Unity version and sample architecture can be embedded cleanly.

Check compatibility with:
- Kotlin and Android Activities.
- The existing Android Gradle configuration.
- UnityPlayer lifecycle.
- Android manifests.
- Gradle dependencies.
- Minimum and target Android SDK versions.
- Firebase dependencies.
- Native Android navigation.
- Android permissions.
- Input and back-button handling.
- App resume and pause behavior.

Do not assume that exporting a Unity project and copying a folder into Android Studio is sufficient.

Do not overwrite the existing Android project's Gradle files or manifest without reviewing and merging the changes.

If embedding Unity is technically blocked or too risky at the current stage, explain why and provide a reversible standalone APK test as a temporary alternative. Do not silently change the intended final architecture.

## 6. IMPLEMENTATION PHASES

Work in the following order.

### PHASE 1 — PROJECT AUDIT

Do not make modifications yet.

Inspect the Unity project and prepare a concise report containing:

1. Exact Unity project path.
2. Unity Editor version.
3. Which scene currently provides multiplayer.
4. Which scene should be used as the starting point.
5. Player prefab path.
6. NetworkManager location.
7. Networking packages and versions.
8. Connection/session flow.
9. Player spawning flow.
10. Existing first-person controls.
11. Camera configuration.
12. Relay/lobby/authentication dependencies.
13. Voice chat implementation and configuration.
14. Android build readiness.
15. Risks involved in modifying the sample.

Check whether the repository's instructions or README specify required Unity Gaming Services configuration.

Identify any setup that is already complete.

Do not recreate services that already work.

At the end, give me the safest recommended next action.

### PHASE 2 — ESTABLISH A REPEATABLE BASELINE

Before changing the project:

1. Open the original multiplayer scene.
2. Enter Play mode.
3. Test the existing connection flow.
4. Verify that two players can connect.
5. Verify that both players spawn.
6. Verify that player movement is synchronized.
7. Verify disconnect behavior if practical.
8. Record any errors in the Console.

Use a standalone build or second client if necessary to test separate instances.

Do not claim that multiplayer is verified simply because the Editor runs.

If a baseline test fails, diagnose it before proceeding.

### PHASE 3 — CREATE VEIL WORLD WITHOUT BREAKING THE SAMPLE

The first environment should be small and simple.

Create a dedicated VEIL social-world scene or a reversible variation of the existing multiplayer scene, depending on the actual scene architecture.

Suggested environment:
- Flat walkable ground.
- A peaceful courtyard or garden.
- Simple paths.
- A few benches.
- Trees and decorative objects.
- A small central meeting area.
- A few landmarks.
- Basic lighting.
- Colliders on walkable surfaces and solid obstacles.

Initially use Unity primitives or existing assets. Avoid paid assets and unnecessary downloads.

Do not spend time on detailed art, realistic graphics, or complex terrain yet.

Requirements:
- Players must not fall through the floor.
- Players must not spawn inside obstacles.
- The scene must support the existing networking setup.
- Other players must remain visible.
- Networked movement must continue to work.
- The environment must work in a standalone build.

Prefer reusing existing player prefabs, scene bootstrap logic, and spawn systems.

Do not add an unrelated second NetworkManager.

### PHASE 4 — FIRST-PERSON PLAYER EXPERIENCE

Inspect the current camera and controls first.

If first-person controls already exist and work, preserve them.

Otherwise, implement the minimum changes needed for:
- First-person camera.
- Keyboard movement for desktop testing.
- Mouse-look controls.
- Android touch-look controls.
- Android virtual joystick or another appropriate mobile movement control.
- Correct local camera ownership.
- Correct network ownership.
- No camera conflicts between clients.

Only the local player should control their own camera and input.

Remote players must still be visible and synchronized.

Do not allow multiple player cameras to control the same view.

Do not remove the existing networking components from the player prefab.

Test two players after every change to player controls or the camera.

### PHASE 5 — RELIABLE MULTIPLAYER SESSION

Reuse the sample's existing authentication, session, Relay, and networking systems wherever possible.

The initial prototype should support:
- Player A hosting or creating a session.
- Player B joining that session.
- Both players entering the same world.
- Both players seeing one another.
- Both players moving independently.
- Players receiving a clean disconnect or connection failure message.

Do not introduce a dedicated server unless there is a demonstrated technical requirement.

Do not hardcode credentials, service secrets, player IDs, or Relay join codes.

If Unity Cloud configuration is incomplete, tell me exactly which service must be configured and how to reach the relevant Unity Dashboard page.

If you cannot access my Unity account or Dashboard, ask me to perform the account-specific step.

### PHASE 6 — BASIC SOCIAL INTERACTION

After the environment and movement are stable, implement a simple text chat.

First inspect whether the existing sample contains a reusable chat or messaging implementation.

If it does, evaluate whether it can be reused safely.

Otherwise, propose a small implementation with:
- A chat input field.
- A send button.
- Messages displayed in a scrollable panel.
- Sender display name.
- Basic message length validation.
- Server-mediated or otherwise appropriately validated message delivery.
- Clear separation between local UI and network messaging.

Do not assume that a local UI update means a message has been sent to other players.

Test actual delivery between two clients.

For display names, initially use a temporary name if necessary. Later we can link the player's VEIL identity.

Do not add Firebase chat storage yet unless it is necessary for the first prototype.

### PHASE 7 — ANDROID BUILD

Once the standalone multiplayer prototype works:

1. Inspect Android Build Profiles.
2. Confirm Android Build Support, SDK, NDK, and JDK availability.
3. Review input and screen orientation.
4. Review graphics API compatibility.
5. Review Android permissions and manifests.
6. Build a standalone Android APK for device testing.
7. Check movement, camera, networking, session joining, and app lifecycle on a real device.

Do not assume a project that works in the Editor automatically works on Android.

Keep the existing PC test path available.

### PHASE 8 — INTEGRATE UNITY INTO VEIL

Inspect both the Unity project and the existing Android Studio project before changing either.

Prepare a technical integration plan using Unity as a Library where feasible.

The target experience should be:

VEIL opens
→ user navigates to VEIL World
→ Unity experience opens within the VEIL application
→ player joins or creates a multiplayer session
→ player explores the world with friends
→ user can return to VEIL's native interface.

Investigate and implement the minimum viable integration in a separate branch or reversible copy.

Preserve the existing Kotlin app and Firebase functionality.

Review:
- Unity library export.
- Gradle project structure.
- Android Activity integration.
- UnityPlayer lifecycle.
- Native-to-Unity communication.
- Back navigation.
- App pause/resume.
- Android permissions.
- Firebase coexistence.
- Build and installation.

Do not copy arbitrary Unity files into the Android project.

Do not merge Gradle configuration blindly.

If the existing Unity version, sample, or Gradle structure is incompatible with the proposed integration, stop and provide the precise issue and a safe alternative.

### PHASE 9 — FUTURE EXTENSIONS

Do not implement these during the initial prototype.

Plan for them later:
- VEIL account identity and display names.
- Firebase friend invitations.
- Joining a world through a VEIL friend invitation.
- Proximity voice chat.
- Interactive objects.
- Emotes and reactions.
- Multiple social locations.
- Separate 2D and 3D multiplayer mini-games.
- Persistent social spaces.
- Moderation, blocking, reporting, and abuse prevention.
- Server-authoritative logic for competitive or valuable interactions.

Keep the initial architecture extensible without prematurely building all of these systems.

## 7. PERFORMANCE AND SECURITY REQUIREMENTS

The intended platform is Android, so avoid unnecessarily expensive graphics and networking.

Prefer:
- Simple geometry initially.
- Reasonable texture sizes.
- Limited unnecessary real-time lights.
- Efficient network updates.
- Synchronizing only the data that needs to be synchronized.
- Clean ownership and authority checks.
- Validated player-generated network messages.
- No client-side trust for privileged actions.
- No hardcoded credentials.
- No public exposure of private user information.

Do not claim that Relay alone makes the entire game secure. Assess authentication, ownership, validation, and abuse prevention separately.

## 8. HOW YOU SHOULD OPERATE

You may inspect the project files, scenes, scripts, prefabs, and configuration using the available Unity integration.

However, connecting to Unity does not automatically mean you can safely inspect or modify every project asset.

Verify what your tools can actually access.

For changes that require Unity Editor interaction, use available supported Unity tools if possible. If you cannot perform an operation directly, tell me exactly what I need to click.

Do not pretend that a scene was modified, a build succeeded, or a multiplayer test passed if you have not verified it.

Prefer small, reviewable changes over rewriting large parts of the project.

Before a major modification, tell me:
1. What you will change.
2. Which assets or files will be affected.
3. Why it is necessary.
4. How it can be rolled back.
5. How you will test it.

If the operation is destructive, affects networking, changes Android build configuration, or requires account permissions, ask for my confirmation first.

Do not ask me questions whose answers can be obtained by inspecting the project.

When a genuine decision is necessary, ask one concise question and explain the available options.

## 9. REQUIRED REPORT FORMAT

After the initial audit, return:

### A. Project understanding
Explain how the existing multiplayer project works.

### B. Existing working components
List what already works and should be preserved.

### C. Risks
List the likely causes of breaking multiplayer or Android integration.

### D. Recommended implementation
Give the safest next step.

### E. Changes made
Initially, report "No changes made" because Phase 1 is inspection only.

### F. Manual steps
Give exact Unity Editor clicks or Android Studio steps I need to perform.

### G. Verification
Give a checklist of what has been tested and what remains unverified.

## 10. IMMEDIATE TASK

Start with PHASE 1 — PROJECT AUDIT ONLY.

Do not modify files, scenes, prefabs, packages, scripts, build settings, or Android configuration yet.

Inspect the actual Unity project connected to the Editor.

Confirm that it is the Unity Technologies U6 First-Person Multiplayer sample, not Boss Room.

Identify the working multiplayer scene, player prefab, NetworkManager, player spawning mechanism, session connection flow, camera, and Android build configuration.

Then report your findings in the required format.

After the audit, wait for my approval before making the first modifications.

Remember: I am a beginner. My priority is a reliable multiplayer VEIL World integrated into my existing Android application, not a rushed collection of features.