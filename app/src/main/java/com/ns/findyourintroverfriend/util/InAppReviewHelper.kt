package com.ns.findyourintroverfriend.util

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

private const val TAG = "InAppReview"

/**
 * Triggers the Google Play In-App Review flow.
 *
 * Google throttles this — it won't show every time, but will show at most
 * a few times per year per user. Best called at high-satisfaction moments:
 *   - After completing Day 3 ceremony
 *   - After winning a bond match
 *   - After entering Party Island
 */
object InAppReviewHelper {

    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val requestTask = manager.requestReviewFlow()
        requestTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // Flow completed — always fires (whether user reviewed or not)
                    Log.d(TAG, "Review flow completed")
                }
            } else {
                // Non-critical — just log it, don't show an error to user
                Log.w(TAG, "Review request failed: ${task.exception?.message}")
            }
        }
    }
}
