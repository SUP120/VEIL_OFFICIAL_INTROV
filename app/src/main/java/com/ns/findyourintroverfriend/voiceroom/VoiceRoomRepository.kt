package com.ns.findyourintroverfriend.voiceroom

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ns.findyourintroverfriend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  VoiceRoomRepository — all Firestore + LiveKit token operations for Whisper
// ─────────────────────────────────────────────────────────────────────────────

class VoiceRoomRepository {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Live rooms flow ───────────────────────────────────────────────────────

    fun liveRoomsFlow(): Flow<List<VoiceRoom>> = callbackFlow {
        // NOTE: Do NOT add .orderBy() here — combining whereEqualTo + orderBy requires
        // a Firestore composite index. Without it Firestore returns FAILED_PRECONDITION
        // which was silently becoming an empty list. We sort client-side instead.
        val reg: ListenerRegistration = db.collection("voiceRooms")
            .whereEqualTo("isLive", true)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("WhisperRepo", "liveRoomsFlow error: ${err.message}", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snap == null) { trySend(emptyList()); return@addSnapshotListener }
                val rooms = snap.documents.mapNotNull { doc ->
                    try { doc.toVoiceRoom() } catch (e: Exception) {
                        android.util.Log.w("WhisperRepo", "Failed to parse room ${doc.id}: ${e.message}")
                        null
                    }
                }
                .filter { it.remainingSecs() > -60L } // 60-s grace for clock skew
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } // newest first
                trySend(rooms)
            }
        awaitClose { reg.remove() }
    }

    // ── Single room flow ──────────────────────────────────────────────────────

    fun roomFlow(roomId: String): Flow<VoiceRoom?> = callbackFlow {
        val reg: ListenerRegistration = db.collection("voiceRooms").document(roomId)
            .addSnapshotListener { doc, err ->
                if (err != null || doc == null || !doc.exists()) { trySend(null); return@addSnapshotListener }
                trySend(try { doc.toVoiceRoom() } catch (_: Exception) { null })
            }
        awaitClose { reg.remove() }
    }

    // ── Create room ───────────────────────────────────────────────────────────

    suspend fun createRoom(
        topic          : String,
        description    : String,
        language       : String,
        tags           : List<String>,
        maxParticipants: Int,
        isLocked       : Boolean,
    ): String {
        val uid     = auth.currentUser?.uid ?: error("Not signed in")
        val userDoc = db.collection("users").document(uid).get().await()
        val alias   = userDoc.getString("alias") ?: auth.currentUser?.displayName ?: "Wanderer"
        val emoji   = userDoc.getString("emoji") ?: "🌙"

        val now       = Timestamp.now()
        val expiresAt = Timestamp(Date(now.toDate().time + WHISPER_FREE_MINS * 60 * 1000L))

        val ref = db.collection("voiceRooms").document()
        ref.set(mapOf(
            "hostUid"          to uid,
            "hostAlias"        to alias,
            "hostEmoji"        to emoji,
            "topic"            to topic.trim().take(60),
            "description"      to description.trim().take(120),
            "language"         to language,
            "isLocked"         to isLocked,
            "tags"             to tags,
            "isLive"           to true,
            "participantUids"  to listOf(uid),
            "participantCount" to 1,
            "maxParticipants"  to maxParticipants,
            "speakerUids"      to emptyList<String>(),
            "joinRequests"     to emptyList<Map<String, Any>>(),
            "raisedHands"      to emptyList<Map<String, Any>>(),
            "mutedByHost"      to emptyList<String>(),
            "kickedUids"       to emptyList<String>(),
            "extensionCount"   to 0,
            "createdAt"        to now,
            "expiresAt"        to expiresAt,
            "closedReason"     to "",
        )).await()
        return ref.id
    }

    // ── Join / Leave ──────────────────────────────────────────────────────────

    suspend fun joinRoomFirestore(roomId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("voiceRooms").document(roomId).update(mapOf(
            "participantUids"  to FieldValue.arrayUnion(uid),
            "participantCount" to FieldValue.increment(1),
        )).await()
    }

    suspend fun leaveRoomFirestore(roomId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("voiceRooms").document(roomId).update(mapOf(
            "participantUids"  to FieldValue.arrayRemove(uid),
            "participantCount" to FieldValue.increment(-1),
            "speakerUids"      to FieldValue.arrayRemove(uid),
            "raisedHands"      to FieldValue.arrayRemove(uid), // best-effort
            "mutedByHost"      to FieldValue.arrayRemove(uid),
        )).await()
    }

    suspend fun closeRoom(roomId: String) {
        db.collection("voiceRooms").document(roomId)
            .update(mapOf("isLive" to false, "closedReason" to "host_closed"))
            .await()
    }

    // ── Join Requests (locked rooms) ──────────────────────────────────────────

    suspend fun requestToJoin(roomId: String) {
        val uid     = auth.currentUser?.uid ?: return
        val userDoc = db.collection("users").document(uid).get().await()
        val alias   = userDoc.getString("alias") ?: auth.currentUser?.displayName ?: "Wanderer"
        val emoji   = userDoc.getString("emoji") ?: "🌙"

        val requestMap = mapOf(
            "uid"        to uid,
            "alias"      to alias,
            "emoji"      to emoji,
            "requestedAt" to System.currentTimeMillis(),
        )
        db.collection("voiceRooms").document(roomId)
            .update("joinRequests", FieldValue.arrayUnion(requestMap))
            .await()
    }

    suspend fun cancelJoinRequest(roomId: String) {
        val uid      = auth.currentUser?.uid ?: return
        // We can't use arrayRemove on a map easily, so we reload & rewrite
        val doc      = db.collection("voiceRooms").document(roomId).get().await()
        val existing = doc.toVoiceRoom().joinRequests.filter { it.uid != uid }
        db.collection("voiceRooms").document(roomId)
            .update("joinRequests", existing.map { it.toMap() })
            .await()
    }

    suspend fun approveJoinRequest(roomId: String, requesterUid: String) {
        val doc      = db.collection("voiceRooms").document(roomId).get().await()
        val room     = doc.toVoiceRoom()
        val updated  = room.joinRequests.filter { it.uid != requesterUid }

        db.collection("voiceRooms").document(roomId).update(mapOf(
            "joinRequests"     to updated.map { it.toMap() },
            "participantUids"  to FieldValue.arrayUnion(requesterUid),
            "participantCount" to FieldValue.increment(1),
        )).await()
    }

    suspend fun rejectJoinRequest(roomId: String, requesterUid: String) {
        val doc     = db.collection("voiceRooms").document(roomId).get().await()
        val updated = doc.toVoiceRoom().joinRequests.filter { it.uid != requesterUid }
        db.collection("voiceRooms").document(roomId)
            .update(mapOf(
                "joinRequests" to updated.map { it.toMap() },
                "kickedUids"   to FieldValue.arrayUnion(requesterUid), // signal rejection
            )).await()
    }

    // ── Raise Hand / Voice Queue ──────────────────────────────────────────────

    suspend fun raiseHand(roomId: String) {
        val uid     = auth.currentUser?.uid ?: return
        val userDoc = db.collection("users").document(uid).get().await()
        val alias   = userDoc.getString("alias") ?: auth.currentUser?.displayName ?: "Wanderer"
        val emoji   = userDoc.getString("emoji") ?: "🌙"

        val handMap = mapOf(
            "uid"      to uid,
            "alias"    to alias,
            "emoji"    to emoji,
            "raisedAt" to System.currentTimeMillis(),
        )
        db.collection("voiceRooms").document(roomId)
            .update("raisedHands", FieldValue.arrayUnion(handMap))
            .await()
    }

    suspend fun lowerHand(roomId: String) {
        val uid  = auth.currentUser?.uid ?: return
        val doc  = db.collection("voiceRooms").document(roomId).get().await()
        val room = doc.toVoiceRoom()
        val updated = room.raisedHands.filter { it.uid != uid }
        db.collection("voiceRooms").document(roomId)
            .update("raisedHands", updated.map { it.toMap() })
            .await()
    }

    /** Host promotes someone from queue to speaker */
    suspend fun promoteToSpeaker(roomId: String, targetUid: String) {
        val doc     = db.collection("voiceRooms").document(roomId).get().await()
        val room    = doc.toVoiceRoom()
        val updatedHands = room.raisedHands.filter { it.uid != targetUid }
        db.collection("voiceRooms").document(roomId).update(mapOf(
            "speakerUids" to FieldValue.arrayUnion(targetUid),
            "raisedHands" to updatedHands.map { it.toMap() },
        )).await()
    }

    /** Host returns a speaker back to audience */
    suspend fun demoteSpeaker(roomId: String, targetUid: String) {
        db.collection("voiceRooms").document(roomId).update(mapOf(
            "speakerUids" to FieldValue.arrayRemove(targetUid),
        )).await()
    }

    // ── Host Controls ─────────────────────────────────────────────────────────

    /** Host mutes a participant — their device watches mutedByHost and self-mutes */
    suspend fun muteParticipant(roomId: String, targetUid: String) {
        db.collection("voiceRooms").document(roomId)
            .update("mutedByHost", FieldValue.arrayUnion(targetUid))
            .await()
    }

    /** Host un-mutes (removes from muted list) */
    suspend fun unmuteParticipant(roomId: String, targetUid: String) {
        db.collection("voiceRooms").document(roomId)
            .update("mutedByHost", FieldValue.arrayRemove(targetUid))
            .await()
    }

    /** Host kicks a participant — their device sees uid in kickedUids and auto-leaves */
    suspend fun kickParticipant(roomId: String, targetUid: String) {
        // Write kicked signal first — target's VM picks it up and leaves
        db.collection("voiceRooms").document(roomId)
            .update("kickedUids", FieldValue.arrayUnion(targetUid))
            .await()
        // Then remove from all lists (host-side cleanup)
        val doc  = db.collection("voiceRooms").document(roomId).get().await()
        val room = doc.toVoiceRoom()
        db.collection("voiceRooms").document(roomId).update(mapOf(
            "participantUids"  to FieldValue.arrayRemove(targetUid),
            "participantCount" to FieldValue.increment(-1),
            "speakerUids"      to FieldValue.arrayRemove(targetUid),
            "raisedHands"      to room.raisedHands.filter { it.uid != targetUid }.map { it.toMap() },
            "mutedByHost"      to FieldValue.arrayRemove(targetUid),
        )).await()
    }

    /** Host locks or unlocks the room mid-session */
    suspend fun setRoomLocked(roomId: String, locked: Boolean) {
        db.collection("voiceRooms").document(roomId)
            .update("isLocked", locked)
            .await()
    }

    // ── Cookie Extension ──────────────────────────────────────────────────────

    /**
     * Extends room by WHISPER_EXTENSION_MINS and deducts WHISPER_EXTENSION_COOKIES
     * from the host's cookieBalance atomically.
     */
    suspend fun extendRoom(roomId: String, hostUid: String): Boolean {
        return try {
            val userRef = db.collection("users").document(hostUid)
            val roomRef = db.collection("voiceRooms").document(roomId)

            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val balance  = (userSnap.getLong("cookieBalance") ?: 0L).toInt()
                if (balance < WHISPER_EXTENSION_COOKIES) throw Exception("insufficient_cookies")

                val roomSnap      = tx.get(roomRef)
                val currentExpiry = roomSnap.getTimestamp("expiresAt")?.toDate()?.time
                    ?: System.currentTimeMillis()
                val newExpiry = Timestamp(Date(currentExpiry + WHISPER_EXTENSION_MINS * 60 * 1000L))
                val extCount  = (roomSnap.getLong("extensionCount") ?: 0L).toInt() + 1

                tx.update(userRef, mapOf(
                    "cookieBalance" to (balance - WHISPER_EXTENSION_COOKIES).toLong(),
                    "cookiesSpent"  to FieldValue.increment(WHISPER_EXTENSION_COOKIES.toLong()),
                ))
                tx.update(roomRef, mapOf(
                    "expiresAt"      to newExpiry,
                    "extensionCount" to extCount.toLong(),
                ))
            }.await()

            // Ledger log (best-effort)
            runCatching {
                db.collection("cookieLedger").document(hostUid)
                    .collection("transactions").add(mapOf(
                        "type"   to "debit",
                        "amount" to WHISPER_EXTENSION_COOKIES,
                        "reason" to "whisper_room_extension",
                        "roomId" to roomId,
                        "ts"     to System.currentTimeMillis(),
                    )).await()
            }
            true
        } catch (_: Exception) { false }
    }

    // ── Participant Aliases (batch fetch) ─────────────────────────────────────

    suspend fun fetchParticipantInfo(uids: List<String>): Pair<Map<String, String>, Map<String, String>> {
        val aliases = mutableMapOf<String, String>()
        val emojis  = mutableMapOf<String, String>()
        // Firestore whereIn supports up to 30 values; rooms max out at 6
        if (uids.isEmpty()) return Pair(aliases, emojis)
        try {
            val snap = db.collection("users")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), uids)
                .get().await()
            for (doc in snap.documents) {
                aliases[doc.id] = doc.getString("alias") ?: "Wanderer"
                emojis[doc.id]  = doc.getString("emoji")  ?: "🌙"
            }
        } catch (_: Exception) {}
        return Pair(aliases, emojis)
    }

    // ── LiveKit token ─────────────────────────────────────────────────────────

    suspend fun fetchLiveKitToken(roomId: String, canPublish: Boolean = true): Pair<String, String> {
        val uid = auth.currentUser?.uid
            ?: error("Not signed in — cannot fetch LiveKit token")

        val tokenUrl = BuildConfig.LIVEKIT_TOKEN_URL
        if (tokenUrl.isBlank()) {
            error(
                "LIVEKIT_TOKEN_URL is not set.\n" +
                "1. Deploy cloudflare-worker/worker.js to Cloudflare (free)\n" +
                "2. Add to local.properties:  livekit.token.url=https://your-worker.workers.dev\n" +
                "3. Rebuild the app"
            )
        }

        val bodyJson = """{"uid":"$uid","identity":"$uid","roomName":"$roomId","canPublish":$canPublish}"""
        val request = Request.Builder()
            .url(tokenUrl)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: error("Empty response from token server")
                if (!response.isSuccessful) error("Token server returned ${response.code}: $body")
                body
            }
        }

        val json       = JSONObject(responseBody)
        val token      = json.optString("token").takeIf { it.isNotBlank() }
            ?: error("Missing 'token' in server response: $responseBody")
        val livekitUrl = json.optString("livekitUrl").takeIf { it.isNotBlank() }
            ?: error("Missing 'livekitUrl' in server response: $responseBody")

        return Pair(token, livekitUrl)
    }

    // ── Invite helpers ────────────────────────────────────────────────────────

    /**
     * Simple model for a person the host can invite (swipe match or feed friend).
     */
    data class InvitableUser(
        val uid       : String = "",
        val alias     : String = "",
        val photoUrl  : String = "",
        val avatarRes : String = "",
        val source    : String = "swipe",  // "swipe" | "friend"
        val matchId   : String = ""        // Firestore matchId — used to write chat message
    )

    /**
     * Fetch all connections for the current user — both swipe matches and feed
     * friends — ready to display in the invite sheet.
     */
    suspend fun getMyConnections(): List<InvitableUser> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid ?: return@withContext emptyList()
        val result = mutableListOf<InvitableUser>()

        try {
            // Read all match IDs from userMatches/{uid}/index
            val indexSnap = db.collection("userMatches").document(myUid)
                .collection("index").get().await()

            for (idxDoc in indexSnap.documents) {
                val matchId = idxDoc.getString("matchId") ?: idxDoc.id
                val matchDoc = runCatching {
                    db.collection("matches").document(matchId).get().await()
                }.getOrNull() ?: continue
                if (!matchDoc.exists()) continue

                val userA    = matchDoc.getString("userA")  ?: continue
                val userB    = matchDoc.getString("userB")  ?: continue
                val source   = matchDoc.getString("source") ?: "swipe"
                val isA      = userA == myUid
                val otherUid = if (isA) userB else userA
                val alias    = if (isA) matchDoc.getString("aliasB") ?: ""
                               else     matchDoc.getString("aliasA") ?: ""
                val photo    = if (isA) matchDoc.getString("photoB") ?: ""
                               else     matchDoc.getString("photoA") ?: ""
                val avatar   = if (isA) matchDoc.getString("avatarB") ?: ""
                               else     matchDoc.getString("avatarA") ?: ""

                result.add(InvitableUser(
                    uid       = otherUid,
                    alias     = alias.ifBlank { "Wanderer" },
                    photoUrl  = photo,
                    avatarRes = avatar,
                    source    = source,
                    matchId   = matchId
                ))
            }
        } catch (e: Exception) {
            android.util.Log.w("VoiceRepo", "getMyConnections: ${e.message}")
        }
        result
    }

    /**
     * Send invite notifications and (for broadcast) mark it in Firestore so it
     * can only be sent once per room.
     *
     * @param roomId        Room being invited into
     * @param roomTitle     Display name shown in the notification
     * @param hostAlias     Host's alias shown in the notification
     * @param targetUids    Empty = broadcast to all; non-empty = personal invites
     * @param connections   Full connection objects (used to find matchIds for chat messages)
     */
    suspend fun inviteToRoom(
        roomId      : String,
        roomTitle   : String,
        hostAlias   : String,
        targetUids  : List<String>,
        connections : List<InvitableUser> = emptyList()
    ) {
        if (targetUids.isEmpty()) {
            // Guard: only allow broadcast once per room
            val roomDoc = db.collection("voiceRooms").document(roomId).get().await()
            if (roomDoc.getBoolean("broadcastSent") == true) return
            db.collection("voiceRooms").document(roomId)
                .update("broadcastSent", true).await()
        } else {
            // Also drop a chat message into each selected person's DM so they
            // see the invite inside their conversation thread.
            val myUid     = auth.currentUser?.uid ?: ""
            val myAlias   = hostAlias
            sendChatInviteMessages(
                roomId      = roomId,
                roomTitle   = roomTitle,
                senderUid   = myUid,
                senderAlias = myAlias,
                targets     = connections.filter { it.uid in targetUids }
            )
        }
        com.ns.findyourintroverfriend.notifications.VeilNotifier.notifyWhisperRoomInvite(
            roomId     = roomId,
            roomTitle  = roomTitle,
            hostAlias  = hostAlias,
            targetUids = targetUids,
            db         = db
        )
    }

    /**
     * Write a "whisper_invite::roomId" message into each selected person's DM.
     * This follows the same convention as game_invite_draw_guess:: — the
     * ConnectionRoomScreen renders it as a tappable invite card.
     */
    private suspend fun sendChatInviteMessages(
        roomId      : String,
        roomTitle   : String,
        senderUid   : String,
        senderAlias : String,
        targets     : List<InvitableUser>
    ) {
        val now = System.currentTimeMillis()
        for (target in targets) {
            val mId = target.matchId
            if (mId.isBlank()) continue
            try {
                db.collection("matches").document(mId)
                    .collection("messages")
                    .add(
                        mapOf(
                            "messageId"   to db.collection("_").document().id,
                            "senderUid"   to senderUid,
                            "senderAlias" to senderAlias,
                            "text"        to "whisper_invite::$roomId::$roomTitle",
                            "sentAt"      to now,
                            "isSticker"   to false,
                            "imageUrl"    to "",
                            "replyToMsgId" to "",
                            "replyToText"  to "",
                            "replyToAlias" to ""
                        )
                    ).await()
                android.util.Log.d("VoiceRepo", "Chat invite sent to match $mId")
            } catch (e: Exception) {
                android.util.Log.w("VoiceRepo", "Chat invite failed for $mId: ${e.message}")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Firestore DocumentSnapshot → VoiceRoom
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
internal fun com.google.firebase.firestore.DocumentSnapshot.toVoiceRoom() = VoiceRoom(
    roomId           = id,
    hostUid          = getString("hostUid")          ?: "",
    hostAlias        = getString("hostAlias")        ?: "Wanderer",
    hostEmoji        = getString("hostEmoji")        ?: "🌙",
    topic            = getString("topic")            ?: "",
    description      = getString("description")      ?: "",
    language         = getString("language")         ?: "",
    isLocked         = getBoolean("isLocked")        ?: false,
    tags             = (get("tags") as? List<*>)?.map { it.toString() } ?: emptyList(),
    isLive           = getBoolean("isLive")          ?: true,
    participantUids  = (get("participantUids") as? List<*>)?.map { it.toString() } ?: emptyList(),
    participantCount = (getLong("participantCount")  ?: 0L).toInt(),
    maxParticipants  = (getLong("maxParticipants")   ?: 6L).toInt(),
    speakerUids      = (get("speakerUids") as? List<*>)?.map { it.toString() } ?: emptyList(),
    joinRequests     = parseJoinRequests(get("joinRequests")),
    raisedHands      = parseRaisedHands(get("raisedHands")),
    mutedByHost      = (get("mutedByHost") as? List<*>)?.map { it.toString() } ?: emptyList(),
    kickedUids       = (get("kickedUids") as? List<*>)?.map { it.toString() } ?: emptyList(),
    extensionCount   = (getLong("extensionCount")    ?: 0L).toInt(),
    createdAt        = getTimestamp("createdAt"),
    expiresAt        = getTimestamp("expiresAt"),
    closedReason     = getString("closedReason")     ?: "",
)

@Suppress("UNCHECKED_CAST")
private fun parseJoinRequests(raw: Any?): List<JoinRequest> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val m = item as? Map<String, Any> ?: return@mapNotNull null
        JoinRequest(
            uid         = m["uid"]?.toString()         ?: return@mapNotNull null,
            alias       = m["alias"]?.toString()       ?: "Wanderer",
            emoji       = m["emoji"]?.toString()       ?: "🌙",
            requestedAt = (m["requestedAt"] as? Long)  ?: 0L,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun parseRaisedHands(raw: Any?): List<RaisedHand> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val m = item as? Map<String, Any> ?: return@mapNotNull null
        RaisedHand(
            uid      = m["uid"]?.toString()      ?: return@mapNotNull null,
            alias    = m["alias"]?.toString()    ?: "Wanderer",
            emoji    = m["emoji"]?.toString()    ?: "🌙",
            raisedAt = (m["raisedAt"] as? Long)  ?: 0L,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers: convert data classes to Firestore maps
// ─────────────────────────────────────────────────────────────────────────────

fun JoinRequest.toMap() = mapOf(
    "uid"         to uid,
    "alias"       to alias,
    "emoji"       to emoji,
    "requestedAt" to requestedAt,
)

fun RaisedHand.toMap() = mapOf(
    "uid"      to uid,
    "alias"    to alias,
    "emoji"    to emoji,
    "raisedAt" to raisedAt,
)
