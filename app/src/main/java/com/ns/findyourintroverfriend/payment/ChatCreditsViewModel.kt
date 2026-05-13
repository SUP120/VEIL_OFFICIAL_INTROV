package com.ns.findyourintroverfriend.payment

// ─────────────────────────────────────────────────────────────────────────────
//  ChatCreditsViewModel — PLAY STORE COMPLIANT STUB
//
//  All Cashfree order creation + verification HTTP calls have been removed.
//  No backend payment endpoints are triggered from this ViewModel.
//
//  TODO (next release): Wire to BillingService for Google Play Billing.
//  See: com.ns.findyourintroverfriend.billing.BillingService
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import com.ns.findyourintroverfriend.chat.ChatMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Credit pack definitions (kept for future Play Billing SKU mapping) ────────
data class CreditPack(
    val id       : String,
    val name     : String,
    val emoji    : String,
    val messages : Int,      // -1 = custom
    val basePrice: Double,
    val tagLine  : String,
)

val CREDIT_PACKS = listOf(
    CreditPack("spark",  "Spark",  "✨", 20, 29.0,  "Keep the spark alive"),
    CreditPack("glow",   "Glow",   "🌟", 60, 49.0,  "Best value · 🔥 Most popular"),
    CreditPack("custom", "Custom", "⚡", -1,  0.10, "Your number, your rules"),
)

val GROUP_DISCOUNTS = listOf(0, 0, 10, 15, 20)   // index = recipient count

// ── Stub state (no active states needed until Play Billing added) ─────────────
sealed class CreditPurchaseState {
    object Idle         : CreditPurchaseState()
    // TODO: Add Purchasing / Success / Failed / Cancelled states when Play Billing is integrated
}

class ChatCreditsViewModel : ViewModel() {

    private val _state   = MutableStateFlow<CreditPurchaseState>(CreditPurchaseState.Idle)
    val state: StateFlow<CreditPurchaseState> = _state.asStateFlow()

    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    // TODO (next release): implement startPurchase(pack: CreditPack) using BillingService
    // TODO (next release): implement giftCredits(recipientUids: List<String>) using BillingService
}
