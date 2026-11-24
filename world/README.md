# VEIL World

The Forest Park is a daytime 3D space with curved gravel trails, a moving fountain, a timber pavilion, wildflowers, picnic spots, and benches. Private islands use the same third-person camera and have a sandy shoreline. The headless Godot server uses ENet over UDP port **7132**. It assigns new players to the first public park with a free spot. A park holds up to **25 players**. The server has a **100-connection playtest cap** across all parks and private rooms.

## Play together

In VEIL, open **Explore → VEIL World → Play**. To play nearby, connect both phones to the same Wi-Fi. On the first phone select **Play together → Host on Wi-Fi**, then open **Room** to view or copy the host address. On the other phone select **Play together**, enter that address and tap **Join park**. Keep the hosting phone in the game. When it leaves, guests return to their solo park. Use a home network that allows devices to communicate with each other.

Select **Trail map** to follow a waypoint. Visit the chime pavilion, butterfly meadow, Stillwater jetty, apple orchard and birdwatch clearing, then try the picnic and shell cove on a private island. Use the activity at each stop to stamp the journal. **Start trail walk** links stops into a timed circuit. The timer starts with the first activity, and stamps and best times are saved for the signed-in player on that device.

While connected, open the map near a stop and choose **Invite room to meet here**. Other players can accept its waypoint from their maps. At the pavilion, two different players ringing the chimes within six seconds play a duet.

### Dedicated Wi-Fi host

Use [Godot 4.5.2](https://godotengine.org/download/archive/4.5.2-stable/). Start this command from the VEIL repository root on the computer hosting the playtest. Replace `godot` with the executable path if it is outside your PATH.

```powershell
godot --headless --path world -- --server
```

Use the same trusted Wi-Fi network and allow UDP **7132** on the host's private network. The example below starts a desktop client on the host and connects it to the park automatically. From another device, use the host's LAN IP in place of `127.0.0.1`. To restrict the server to the host computer, add `--bind=127.0.0.1` after `--server`.

Run the current world pack and server code together. The room and matchmaking updates change the game protocol.

```powershell
godot --path world -- --connect=127.0.0.1 --alias=Ash
```

Move with **WASD** or the arrow keys. Hold the right mouse button and drag to orbit. Scroll to zoom and use **R** or **Reset view** to restore the starting angle. Movement follows the camera direction. Near a bench, press **E** or click **Sit**. Press **H** or click **Wave** to greet someone.

## Open the park from VEIL

Both debug and release include the Godot activity and world pack. Play connects to `wss://veil-world.onrender.com/world`. To try a LAN host by default in development, override both properties below in your user Gradle properties.

On your development computer, set the server IP in `~/.gradle/gradle.properties`. Use the IP of the computer running the server.

```properties
veilWorldOnlineUrl=
veilWorldHost=192.168.1.20
```

The **debug** variant connects to this LAN host automatically. Use the left thumb control to move, drag the scenery to look around, and pinch to zoom. If the configured server is unavailable, players can explore solo and select **Retry park**.

The LAN server trusts the alias sent by the client. Keep the LAN playtest on a trusted network. See [Hosted playtest](#hosted-playtest) to connect through an internet endpoint with verified VEIL accounts. The park's **Whisper** button opens VEIL's existing voice lobby. There is no island-specific voice room.

## Create a private island

While connected, open **Islands**, choose **2, 3, 4, or 5 players**, and create an island. The number includes you. Share the invite code with friends, who can join from the **Islands** menu in any park on the same server. Players see and interact with people in their current room. Select **Return to park** to leave. The server prefers the park you came from if it has space. An island and its code are removed when the last player leaves or disconnects. The playtest allows up to six invite islands at once.

## Meet one player privately

In a public park, select **Match → Find a match**. The server pairs available players from any of its parks in the order they opt in. You can close the menu and continue exploring while you wait. When **Match ready** appears, each player has up to 20 seconds to accept. When both accept, only those two players enter the match island. Either person can end the match with **Match → Return to park**. Both players return to available public parks. If one disconnects, the other returns automatically. A private match has no invite code and uses no voice chat.

## Refresh the Android world pack

From the VEIL repository root, run the following after editing the Godot project.

```powershell
godot --headless --path world --export-pack "Android World Pack" "../app/src/veilWorld/assets/veil_world.pck"
```

## Render online playtest

Run `scripts/package-veil-world-render.ps1` from PowerShell after refreshing the Android world pack. Extract `world/build/render-upload/veil-world-render.zip` and upload its three files to the root of a private GitHub repository. The archive contains the same game pack used by Android, a Dockerfile and a Render Blueprint.

1. In Render select **New → Blueprint**, connect the server repository, and review the single **Free** web service in **Singapore**. The Blueprint uses Render's TCP readiness check for the game socket.
2. During setup enter `VEIL_FIREBASE_WEB_API_KEY` for the Firebase project `introver-match-app`. Use a Firebase client API key permitted to call the Identity Toolkit API from a server. An Android-app-restricted key can be rejected from Render. The Blueprint already supplies the project ID.
3. Deploy. Wait for **Live** and the log message **VEIL World authenticated lobby listening**. Copy the HTTPS URL shown on the service dashboard. Opening it in a normal browser does not join the game because the server expects a WebSocket upgrade.
4. Use the assigned hostname in `veilWorldOnlineUrl=wss://<assigned-host>.onrender.com/world` in Gradle properties for the next Android build. Play automatically joins a public park. **Play together → Join public world** reconnects after leaving. A sleeping service is retried for up to 100 seconds, and **Cancel join** returns immediately to solo.
5. On two different networks, sign in with two VEIL accounts, enter a public park, then create a private island and join with its code. A server restart ends active rooms. Each public park holds 25 people. An invite island holds 2–5, including its creator.

Free instances can sleep or exhaust their usage allowance. Start with an internal playtest. [Render free service limits](https://render.com/docs/free)

## Hosted playtest

Use a Linux VM that can stay running. Oracle Cloud's Always Free Ampere A1 shape is a starting option. Availability depends on your account's home region, and Oracle can reclaim idle instances. [Oracle Always Free limits](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)

1. Put the Godot 4.5.2 Linux binary for the VM's architecture and the exported world pack at `/opt/veil-world/godot` and `/opt/veil-world/veil_world.pck`. Make the Godot binary executable.
2. Create the `veil-world` system user and group. Create `/etc/veil-world.env` with `VEIL_FIREBASE_PROJECT_ID` and `VEIL_FIREBASE_WEB_API_KEY` for the same Firebase project as the app. Install [veil-world.service](deploy/veil-world.service) to `/etc/systemd/system/` and run `sudo systemctl daemon-reload` followed by `sudo systemctl enable --now veil-world`.
3. Install Caddy and point a hostname at the VM. Update [Caddyfile](deploy/Caddyfile), copy it to `/etc/caddy/Caddyfile`, and reload Caddy. Allow TCP **80** and **443** in the cloud and host firewalls for Caddy. Godot listens on **127.0.0.1:7133** so Firebase sign-in tokens go through the TLS endpoint. Keep **7133** and the LAN server port private. [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
4. Add `veilWorldOnlineUrl=wss://world.example.org/world` with your real address to your Gradle properties. Debug builds use that endpoint automatically when players tap **Play**.

The hosted server verifies Firebase ID tokens, gets the alias from the signed-in user's profile, and checks their `safetyRestrictions` document on entry. The deployed Firestore rules must let users read their own restriction document and profile. These checks use Firebase Auth and Firestore directly.

The project configures Render for both build types. An empty `veilWorldOnlineUrl` enables a solo and Wi-Fi build. For a hosted playtest, use matching server and client pack versions and verify a signed-in connection from a second network.
