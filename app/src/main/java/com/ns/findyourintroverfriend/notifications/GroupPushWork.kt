package com.ns.findyourintroverfriend.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.groupchat.GROUP_MESSAGE_LIMIT
import com.ns.findyourintroverfriend.groupchat.GroupChatMessages
import com.ns.findyourintroverfriend.groupchat.groupDocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val ROOM = "roomId"
private const val ROOM_NAME = "roomName"
private const val UID = "uid"
private const val MESSAGE = "messageId"
private const val REPLY = "inReplyTo"
private const val TEXT = "text"
private const val MAX_ATTEMPTS = 3

private val groupWorkNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

class GroupNotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GroupPushNotification.ACTION_REPLY) return
        val uid = intent.getStringExtra(UID) ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val roomId = intent.getStringExtra(ROOM)?.takeIf(groupDocumentId::matches) ?: return
        val roomName = intent.getStringExtra(ROOM_NAME).orEmpty().take(80)
        val replyId = intent.getStringExtra(REPLY)?.takeIf(groupDocumentId::matches)
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(GroupPushNotification.REPLY_KEY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        if (text.length > GROUP_MESSAGE_LIMIT) {
            GroupPushNotification.showReplyState(context, uid, roomId, roomName, replyId, "Reply is too long. Open VEIL to send it.")
            return
        }
        val request = OneTimeWorkRequestBuilder<GroupNotificationReplyWorker>()
            .setConstraints(groupWorkNetwork)
            .setInputData(workDataOf(
                UID to uid, ROOM to roomId, ROOM_NAME to roomName, REPLY to replyId,
                MESSAGE to UUID.randomUUID().toString(), TEXT to text
            ))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        GroupPushNotification.showReplyState(context, uid, roomId, roomName, replyId, "Sending reply…", allowReply = false)
    }
}

class GroupNotificationReplyWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(UID) ?: return Result.failure()
        val room = inputData.getString(ROOM) ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.failure()
        val roomName = inputData.getString(ROOM_NAME).orEmpty()
        val message = inputData.getString(MESSAGE) ?: return Result.failure()
        val text = inputData.getString(TEXT) ?: return Result.failure()
        val replyId = inputData.getString(REPLY)
        return try {
            GroupChatMessages.send(room, uid, message, text, replyId)
            GroupPushRelay.enqueue(applicationContext, uid, room, message)
            GroupPushNotification.showReplyState(applicationContext, uid, room, roomName, replyId, "Reply sent")
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val code = (error as? FirebaseFirestoreException)?.code
            val retryable = code in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED
            )
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                GroupPushNotification.showReplyState(
                    applicationContext, uid, room, roomName, replyId,
                    "Reply couldn't be sent. Open VEIL to try again."
                )
                Result.failure()
            }
        }
    }
}

internal object GroupPushRelay {
    fun enqueue(context: Context, uid: String, room: String, message: String) {
        if (BuildConfig.GROUP_PUSH_RELAY_URL.isBlank()) return
        val request = OneTimeWorkRequestBuilder<GroupPushRelayWorker>()
            .setConstraints(groupWorkNetwork)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf(UID to uid, ROOM to room, MESSAGE to message))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("group-push-$room-$message", ExistingWorkPolicy.KEEP, request)
    }
}

class GroupPushRelayWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(UID) ?: return Result.failure()
        val user = FirebaseAuth.getInstance().currentUser?.takeIf { it.uid == uid } ?: return Result.failure()
        val room = inputData.getString(ROOM) ?: return Result.failure()
        val message = inputData.getString(MESSAGE) ?: return Result.failure()
        val url = BuildConfig.GROUP_PUSH_RELAY_URL.takeIf(String::isNotBlank) ?: return Result.success()
        return try {
            val token = user.getIdToken(false).await().token ?: return Result.failure()
            val checkToken = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("X-Firebase-AppCheck", checkToken)
                .post(JSONObject().put(ROOM, room).put(MESSAGE, message).toString().toRequestBody("application/json".toMediaType()))
                .build()
            val status = withContext(Dispatchers.IO) {
                var code = 202
                repeat(4) {
                    if (code == 202) code = http.newCall(request).execute().use { it.code }
                }
                code
            }
            when (status) {
                200, 204 -> Result.success()
                202, 409, 429, in 500..599 -> retryIfPossible()
                else -> Result.failure()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            retryIfPossible()
        }
    }

    private fun retryIfPossible() = if (runAttemptCount < 8) Result.retry() else Result.failure()

    private companion object {
        val http = OkHttpClient.Builder().callTimeout(24, TimeUnit.SECONDS).followRedirects(false)
            .followSslRedirects(false).build()
    }
}
