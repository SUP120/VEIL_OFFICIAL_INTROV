package com.ns.findyourintroverfriend.billing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class CookieViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid ?: ""

    private val _cookieBalance       = MutableStateFlow(0)
    val cookieBalance: StateFlow<Int> = _cookieBalance.asStateFlow()

    private val _freeComplimentsLeft       = MutableStateFlow(3)
    val freeComplimentsLeft: StateFlow<Int> = _freeComplimentsLeft.asStateFlow()

    private val _isSpending       = MutableStateFlow(false)
    val isSpending: StateFlow<Boolean> = _isSpending.asStateFlow()

    private var listener: ListenerRegistration? = null

    init { if (uid.isNotEmpty()) startListening() }

    private fun startListening() {
        listener = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener

                // ── Auto-initialize fields for users who've never bought cookies ──
                if (!snap.contains("cookieBalance")) {
                    snap.reference.update(mapOf(
                        "cookieBalance"                to 0L,
                        "freeComplimentsUsedThisMonth" to 0L,
                        "freeComplimentsResetMonth"    to -1L,
                        "cookiesEarned"                to 0L,
                        "cookiesSpent"                 to 0L
                    )).addOnFailureListener { /* doc exists — safe to ignore */ }
                }

                _cookieBalance.value = (snap.getLong("cookieBalance") ?: 0L).toInt()

                // Monthly free-compliment tracking
                val usedThisMonth = (snap.getLong("freeComplimentsUsedThisMonth") ?: 0L).toInt()
                val resetMonth    = (snap.getLong("freeComplimentsResetMonth") ?: -1L).toInt()
                val currentMonth  = currentYearMonth()
                val effective     = if (resetMonth == currentMonth) usedThisMonth else 0
                _freeComplimentsLeft.value = maxOf(0, 3 - effective)
            }
    }

    /**
     * Spend [amount] cookies for [reason]. Atomic transaction — only succeeds
     * if balance >= amount. Calls [onResult](true) on success, (false) on failure.
     */
    fun spendCookies(amount: Int, reason: String, onResult: (Boolean) -> Unit) {
        if (uid.isEmpty()) { onResult(false); return }
        viewModelScope.launch {
            _isSpending.value = true
            try {
                val userRef = db.collection("users").document(uid)
                db.runTransaction { tx ->
                    val snap    = tx.get(userRef)
                    val balance = (snap.getLong("cookieBalance") ?: 0L).toInt()
                    if (balance < amount) throw Exception("insufficient")
                    val newBalance = balance - amount
                    val spent      = (snap.getLong("cookiesSpent") ?: 0L) + amount
                    tx.update(userRef, mapOf(
                        "cookieBalance" to newBalance.toLong(),
                        "cookiesSpent"  to spent
                    ))
                }.await()
                // Best-effort ledger log
                runCatching {
                    db.collection("cookieLedger").document(uid)
                        .collection("transactions").add(mapOf(
                            "type"   to "debit",
                            "amount" to amount,
                            "reason" to reason,
                            "ts"     to System.currentTimeMillis()
                        )).await()
                }
                onResult(true)
            } catch (e: Exception) {
                Log.e("CookieVM", "spendCookies failed: ${e.message}")
                onResult(false)
            } finally {
                _isSpending.value = false
            }
        }
    }

    /**
     * Claim one free monthly compliment.
     * [onResult](success, isFree) — isFree=true means no cookie cost.
     */
    fun claimFreeCompliment(onResult: (success: Boolean, isFree: Boolean) -> Unit) {
        if (uid.isEmpty()) { onResult(false, false); return }
        viewModelScope.launch {
            try {
                val userRef      = db.collection("users").document(uid)
                val currentMonth = currentYearMonth()
                db.runTransaction { tx ->
                    val snap  = tx.get(userRef)
                    val reset = (snap.getLong("freeComplimentsResetMonth") ?: -1L).toInt()
                    val used  = if (reset == currentMonth)
                        (snap.getLong("freeComplimentsUsedThisMonth") ?: 0L).toInt() else 0
                    if (used >= 3) throw Exception("no_free")
                    tx.update(userRef, mapOf(
                        "freeComplimentsUsedThisMonth" to (used + 1).toLong(),
                        "freeComplimentsResetMonth"    to currentMonth.toLong()
                    ))
                }.await()
                onResult(true, true)
            } catch (e: Exception) {
                onResult(false, false)
            }
        }
    }

    // Returns YYYYMM as an Int, e.g. 202505 for May 2025
    private fun currentYearMonth(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
