package com.ns.findyourintroverfriend.payment

// ─────────────────────────────────────────────────────────────────────────────
//  PaymentViewModel — PLAY STORE COMPLIANT STUB
//
//  All Cashfree order creation + verification HTTP calls have been removed.
//  No backend payment endpoints are triggered from this ViewModel.
//
//  TODO (next release): Wire to BillingService for Google Play Billing.
//  See: com.ns.findyourintroverfriend.billing.BillingService
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Stub state ────────────────────────────────────────────────────────────────
sealed class PaymentState {
    object Idle              : PaymentState()
    // TODO: Add Purchasing / Success / Failed states when Play Billing is integrated
}

// ── Unlock status (read-only from Firestore — no payment trigger) ─────────────
data class UnlockStatus(
    val chatLimitUnlocked : Boolean = false,
    val chatMsgLimit      : Int     = 7,
    val stickersUnlocked  : Boolean = false
)

class PaymentViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val _unlockStatus = MutableStateFlow(UnlockStatus())
    val unlockStatus: StateFlow<UnlockStatus> = _unlockStatus.asStateFlow()

    init { loadUnlockStatus() }

    /** Read current unlock state from Firestore (no payment triggered). */
    private fun loadUnlockStatus() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                _unlockStatus.value = UnlockStatus(
                    chatLimitUnlocked = doc.getBoolean("chatLimitUnlocked") ?: false,
                    chatMsgLimit      = (doc.getLong("chatMsgLimit")?.toInt()) ?: 7,
                    stickersUnlocked  = doc.getBoolean("stickersUnlocked") ?: false
                )
            } catch (_: Exception) {}
        }
    }

    // TODO (next release): fun startChatUnlockPurchase() — use BillingService.launchBillingFlow()
    // TODO (next release): fun onPurchaseResult(purchase: Purchase) — verify + grant entitlement
}
