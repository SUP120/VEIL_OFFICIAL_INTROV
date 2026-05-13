package com.ns.findyourintroverfriend.waiting

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  QueueHeartbeatWorker
//
//  Runs every 15 minutes (WorkManager minimum) while the user is in
//  backgroundMode queue. Updates heartbeatAt so that other active users'
//  stale-check code doesn't remove this entry.
//
//  Self-cancels if:
//    • The queue entry is gone (user was matched or cancelled)
//    • User is no longer signed in
//
//  Schedule on: vm.stayInQueueAndLeave()
//  Cancel on:   vm.cancelBackgroundQueue() OR group formation
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "QueueHeartbeat"

class QueueHeartbeatWorker(
    ctx   : Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    companion object {
        const val WORK_NAME = "veil_queue_heartbeat"

        /** Schedule a periodic heartbeat — idempotent (KEEP policy) */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<QueueHeartbeatWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,   // don't restart if already running
                    req
                )
            Log.d(TAG, "Heartbeat worker scheduled")
        }

        /** Cancel the heartbeat — call when user leaves queue or group forms */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Heartbeat worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "No signed-in user — cancelling worker")
            cancel(applicationContext)
            return Result.success()
        }

        val db = FirebaseFirestore.getInstance()

        return try {
            val queueRef = db.collection("matchmaking_queue").document(uid)
            val doc      = queueRef.get().await()

            when {
                // User was matched or cancelled — stop the worker
                !doc.exists() -> {
                    Log.d(TAG, "Queue entry gone — self-cancelling worker")
                    cancel(applicationContext)
                    Result.success()
                }
                doc.getString("status") != "waiting" -> {
                    Log.d(TAG, "Queue status no longer 'waiting' — cancelling worker")
                    cancel(applicationContext)
                    Result.success()
                }
                else -> {
                    // Still in queue — refresh heartbeat
                    queueRef.update(
                        mapOf(
                            "heartbeatAt"    to Timestamp.now(),
                            "backgroundMode" to true             // ensure flag stays set
                        )
                    ).await()
                    Log.d(TAG, "Heartbeat sent for uid=$uid")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat failed — retrying: ${e.message}")
            Result.retry()
        }
    }
}
