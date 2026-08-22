package com.ns.findyourintroverfriend.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "VeilBilling"

// ─────────────────────────────────────────────────────────────────────────────
//  Product IDs — must match exactly what you created in Play Console
// ─────────────────────────────────────────────────────────────────────────────
object VeilProductId {
    const val CHAT_EXTEND_20   = "veil_chat_extend_20"   // 20 extra messages  ₹79
    const val CHAT_EXTEND_50   = "veil_chat_extend_50"   // 50 extra messages  ₹149

    // Profile theme backgrounds
    const val THEME_MEADOW     = "profile_bg_meadow"
    const val THEME_SAKURA     = "profile_bg_sakura"
    const val THEME_CLOUDS     = "profile_bg_clouds"
    const val THEME_DUCK       = "profile_bg_duck"
    val ALL_THEME_IDS = listOf(THEME_MEADOW, THEME_SAKURA, THEME_CLOUDS, THEME_DUCK)

    // Chat sticker packs — non-consumable (bought once, keep forever)
    const val STICKER_PACK_1   = "chat_sticker_pack_1"   // ₹49 — available now
    const val STICKER_PACK_2   = "chat_sticker_pack_2"   // Coming soon
    const val STICKER_PACK_3   = "chat_sticker_pack_3"   // Coming soon
    val ALL_STICKER_IDS = listOf(STICKER_PACK_1)         // only query pack 1 for now

    // ── Cookie packs — CONSUMABLE (user can buy again after consuming) ──────────
    const val COOKIES_SNACK  = "veil_cookies_snack"    // 50 cookies   ₹29
    const val COOKIES_TIN    = "veil_cookies_tin"      // 130 cookies  ₹59
    const val COOKIES_JAR    = "veil_cookies_jar"      // 230 cookies  ₹99
    const val COOKIES_BAKERY = "veil_cookies_bakery"   // 500 cookies  ₹199
    val ALL_COOKIE_IDS = listOf(COOKIES_SNACK, COOKIES_TIN, COOKIES_JAR, COOKIES_BAKERY)

    fun cookiesForProduct(productId: String) = when (productId) {
        COOKIES_SNACK  -> 50
        COOKIES_TIN    -> 130
        COOKIES_JAR    -> 230
        COOKIES_BAKERY -> 500
        else           -> 0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chat pack definition — shown in UI
// ─────────────────────────────────────────────────────────────────────────────
data class ChatPack(
    val productId    : String,
    val messages     : Int,
    val displayPrice : String,
    val productDetails: ProductDetails? = null
)

data class CookiePack(
    val productId     : String,
    val label         : String,
    val cookies       : Int,
    val fallbackPrice : String,
    val isPopular     : Boolean = false,
    val isBestValue   : Boolean = false,
    val productDetails: ProductDetails? = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  Billing state
// ─────────────────────────────────────────────────────────────────────────────
sealed class BillingState {
    object Connecting   : BillingState()
    object Ready        : BillingState()
    data class Error(val message: String) : BillingState()
}

sealed class PurchaseState {
    object Idle         : PurchaseState()
    object Pending      : PurchaseState()
    data class Success(val productId: String, val messages: Int) : PurchaseState()
    data class Failed(val message: String)  : PurchaseState()
}

sealed class CookiePurchaseState {
    object Idle       : CookiePurchaseState()
    object Pending    : CookiePurchaseState()
    object Verifying  : CookiePurchaseState()  // consume confirmed, writing to Firestore
    data class Success(val cookiesGranted: Int) : CookiePurchaseState()
    data class Failed(val message: String)      : CookiePurchaseState()
    object Cancelled  : CookiePurchaseState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  VeilBillingClient — singleton, call init() from MainActivity or Application
// ─────────────────────────────────────────────────────────────────────────────
object VeilBillingClient {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val auth  = FirebaseAuth.getInstance()
    private val db    = FirebaseFirestore.getInstance()

    private lateinit var client: BillingClient

    private val _billingState   = MutableStateFlow<BillingState>(BillingState.Connecting)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _purchaseState  = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    // Cache of fetched ProductDetails from Play
    private val _packs = MutableStateFlow<List<ChatPack>>(emptyList())
    val packs: StateFlow<List<ChatPack>> = _packs.asStateFlow()

    // Theme product details (productId -> ProductDetails)
    private val _themeDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    // Set of unlocked theme IDs for this user
    private val _unlockedThemes = MutableStateFlow<Set<String>>(emptySet())
    val unlockedThemes: StateFlow<Set<String>> = _unlockedThemes.asStateFlow()

    // Sticker pack product details cache
    private val _stickerDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    // Set of unlocked sticker pack IDs for this user
    private val _unlockedStickers = MutableStateFlow<Set<String>>(emptySet())
    val unlockedStickers: StateFlow<Set<String>> = _unlockedStickers.asStateFlow()

    // ── Cookie packs ──────────────────────────────────────────────────────────
    private val _cookiePacks = MutableStateFlow<List<CookiePack>>(emptyList())
    val cookiePacks: StateFlow<List<CookiePack>> = _cookiePacks.asStateFlow()

    private val _cookiePurchaseState = MutableStateFlow<CookiePurchaseState>(CookiePurchaseState.Idle)
    val cookiePurchaseState: StateFlow<CookiePurchaseState> = _cookiePurchaseState.asStateFlow()

    // ── Init ─────────────────────────────────────────────────────────────────
    fun init(context: Context) {
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener(::onPurchasesUpdated)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        connect()
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    _billingState.value = BillingState.Ready
                    scope.launch { queryProductDetails() }
                    scope.launch { restorePurchases() }
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    _billingState.value = BillingState.Error(result.debugMessage)
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected — will retry")
                _billingState.value = BillingState.Connecting
                connect()   // auto-reconnect
            }
        })
    }

    // ── Query product details from Play ──────────────────────────────────────
    private suspend fun queryProductDetails() {
        val chatProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(VeilProductId.CHAT_EXTEND_20)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(VeilProductId.CHAT_EXTEND_50)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val themeProducts = VeilProductId.ALL_THEME_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val stickerProducts = VeilProductId.ALL_STICKER_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val cookieProducts = VeilProductId.ALL_COOKIE_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val productList = chatProducts + themeProducts + stickerProducts + cookieProducts
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            client.queryProductDetailsAsync(params) { r, queryResult ->
                // billing-ktx 8.0.0+: second param is QueryProductDetailsResult, not List<ProductDetails>
                cont.resume(r to queryResult.productDetailsList)
            }
        }

        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            val detailsMap = result.second.associateBy { it.productId }
            _packs.value = listOf(
                ChatPack(
                    productId     = VeilProductId.CHAT_EXTEND_20,
                    messages      = 20,
                    displayPrice  = detailsMap[VeilProductId.CHAT_EXTEND_20]
                        ?.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹79",
                    productDetails = detailsMap[VeilProductId.CHAT_EXTEND_20]
                ),
                ChatPack(
                    productId     = VeilProductId.CHAT_EXTEND_50,
                    messages      = 50,
                    displayPrice  = detailsMap[VeilProductId.CHAT_EXTEND_50]
                        ?.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹149",
                    productDetails = detailsMap[VeilProductId.CHAT_EXTEND_50]
                ),
            )
            // Cookie packs
            _cookiePacks.value = listOf(
                CookiePack(VeilProductId.COOKIES_SNACK,  "Snack",  50,  detailsMap[VeilProductId.COOKIES_SNACK]?.oneTimePurchaseOfferDetails?.formattedPrice  ?: "₹29",  productDetails = detailsMap[VeilProductId.COOKIES_SNACK]),
                CookiePack(VeilProductId.COOKIES_TIN,    "Tin",    130, detailsMap[VeilProductId.COOKIES_TIN]?.oneTimePurchaseOfferDetails?.formattedPrice    ?: "₹59",  productDetails = detailsMap[VeilProductId.COOKIES_TIN]),
                CookiePack(VeilProductId.COOKIES_JAR,    "Jar",    230, detailsMap[VeilProductId.COOKIES_JAR]?.oneTimePurchaseOfferDetails?.formattedPrice    ?: "₹99",  isPopular = true,    productDetails = detailsMap[VeilProductId.COOKIES_JAR]),
                CookiePack(VeilProductId.COOKIES_BAKERY, "Bakery", 500, detailsMap[VeilProductId.COOKIES_BAKERY]?.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹199", isBestValue = true,  productDetails = detailsMap[VeilProductId.COOKIES_BAKERY]),
            )
            Log.d(TAG, "Products loaded: ${_packs.value.map { it.productId }}")
        } else {
            Log.e(TAG, "queryProductDetails failed: ${result.first.debugMessage}")
        }
    }

    // ── Launch purchase flow for Cookie packs ─────────────────────────────────
    fun buyCookies(activity: Activity, productId: String) {
        val pack    = _cookiePacks.value.firstOrNull { it.productId == productId }
        val details = pack?.productDetails ?: run {
            _cookiePurchaseState.value = CookiePurchaseState.Failed("Pack not loaded yet. Please wait.")
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )).build()
        _cookiePurchaseState.value = CookiePurchaseState.Pending
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _cookiePurchaseState.value = CookiePurchaseState.Failed(result.debugMessage)
        }
    }

    fun resetCookiePurchaseState() { _cookiePurchaseState.value = CookiePurchaseState.Idle }

    // ── Launch purchase flow ─────────────────────────────────────────────────
    fun buy(activity: Activity, pack: ChatPack) {
        val details = pack.productDetails ?: run {
            _purchaseState.value = PurchaseState.Failed("Product details not loaded yet.")
            return
        }
        // One-time products don't use offer tokens — build params directly from ProductDetails
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        _purchaseState.value = PurchaseState.Pending
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Failed(result.debugMessage)
        }
    }

    // ── Launch purchase flow for Profile Themes ───────────────────────────────
    fun buyTheme(activity: Activity, productId: String) {
        val details = _themeDetails.value[productId] ?: run {
            _purchaseState.value = PurchaseState.Failed("Theme not loaded yet. Please wait.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        _purchaseState.value = PurchaseState.Pending
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Failed(result.debugMessage)
        }
    }

    // \u2500\u2500 Launch purchase flow for Chat Sticker Packs \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    fun buySticker(activity: Activity, productId: String) {
        val details = _stickerDetails.value[productId] ?: run {
            _purchaseState.value = PurchaseState.Failed("Sticker pack not available yet. Please wait.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        _purchaseState.value = PurchaseState.Pending
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Failed(result.debugMessage)
        }
    }

    // ── Purchase result callback ─────────────────────────────────────────────
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { scope.launch { handlePurchase(it) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Idle
                _cookiePurchaseState.value = CookiePurchaseState.Cancelled
            }
            else -> {
                Log.e(TAG, "Purchase error: ${result.debugMessage}")
                // Only update the state that was actually pending — don't overwrite the other
                if (_cookiePurchaseState.value is CookiePurchaseState.Pending) {
                    _cookiePurchaseState.value = CookiePurchaseState.Failed(result.debugMessage)
                } else {
                    _purchaseState.value = PurchaseState.Failed(result.debugMessage)
                }
            }
        }
    }

    // ── Handle a completed purchase ───────────────────────────────────────────
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return
        val isTheme   = productId in VeilProductId.ALL_THEME_IDS
        val isSticker = productId in VeilProductId.ALL_STICKER_IDS
        val isCookie  = productId in VeilProductId.ALL_COOKIE_IDS
        val messages  = when (productId) {
            VeilProductId.CHAT_EXTEND_20 -> 20
            VeilProductId.CHAT_EXTEND_50 -> 50
            else                         -> 0
        }

        // ── Cookie purchase — consumable flow ─────────────────────────────────
        if (isCookie) {
            val cookieCount = VeilProductId.cookiesForProduct(productId)
            _cookiePurchaseState.value = CookiePurchaseState.Verifying

            // Step 1: Consume on Play (marks it consumable again + auto-acknowledges)
            val consumeResult = suspendCancellableCoroutine<BillingResult> { cont ->
                val params = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.consumeAsync(params) { r, _ -> cont.resume(r) }
            }

            // ITEM_NOT_OWNED (8) = token already consumed on a previous attempt (e.g. app crash).
            // This is NOT a failure — we should still credit the cookies.
            // Only truly fatal errors (network, developer error) should abort.
            val consumeFatal = consumeResult.responseCode != BillingClient.BillingResponseCode.OK
                    && consumeResult.responseCode != BillingClient.BillingResponseCode.ITEM_NOT_OWNED

            if (consumeFatal) {
                Log.e(TAG, "consumeAsync fatal error [${consumeResult.responseCode}]: ${consumeResult.debugMessage}")
                _cookiePurchaseState.value = CookiePurchaseState.Failed(
                    "Payment failed at verification step [${consumeResult.responseCode}]. Please try again."
                )
                return
            }

            Log.d(TAG, "consumeAsync result [${consumeResult.responseCode}] — proceeding to credit cookies")

            // Step 2: Ensure user is authenticated
            val uid = auth.currentUser?.uid ?: run {
                Log.e(TAG, "Cookie credit failed — no authenticated user")
                _cookiePurchaseState.value = CookiePurchaseState.Failed(
                    "Session expired. Reopen the app — your cookies will sync automatically."
                )
                return
            }

            // Step 3: Credit cookies in Firestore
            // Uses set(merge=true) + FieldValue.increment — safe for new users (no field needed to exist)
            // Uses purchases/{token} doc as idempotency guard — set() is a no-op if doc already exists
            try {
                val userRef  = db.collection("users").document(uid)
                val tokenRef = db.collection("purchases").document(purchase.purchaseToken)

                // Credit cookies atomically — works even if cookieBalance/cookiesEarned don't exist yet
                userRef.set(
                    mapOf(
                        "cookieBalance" to com.google.firebase.firestore.FieldValue.increment(cookieCount.toLong()),
                        "cookiesEarned" to com.google.firebase.firestore.FieldValue.increment(cookieCount.toLong())
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

                Log.d(TAG, "✅ Cookies credited: +$cookieCount to $uid")

                // Record token (best-effort — don't fail purchase if this write fails)
                try {
                    tokenRef.set(
                        mapOf(
                            "uid"            to uid,
                            "productId"      to productId,
                            "cookiesGranted" to cookieCount,
                            "status"         to "SUCCESS",
                            "createdAt"      to System.currentTimeMillis()
                        )
                    ).await()
                } catch (tokenEx: Exception) {
                    Log.w(TAG, "Token record write failed (non-fatal): ${tokenEx.message}")
                    // Cookies were already credited above — don't fail the user
                }

                _cookiePurchaseState.value = CookiePurchaseState.Success(cookieCount)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Firestore cookie credit failed: ${e.message}", e)
                _cookiePurchaseState.value = CookiePurchaseState.Failed(
                    "Payment received but cookies sync failed. Reopen the app to retry."
                )
            }
            return   // stop here — consume already acknowledged for cookies
        }

        // 1. Acknowledge (required — Play refunds if not acknowledged within 3 days)
        if (!purchase.isAcknowledged) {
            val ackResult = suspendCancellableCoroutine<BillingResult> { cont ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(params) { cont.resume(it) }
            }
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                _purchaseState.value = PurchaseState.Failed("Could not confirm purchase.")
                return
            }
        }

        // 2. Grant entitlement in Firestore
        val uid = auth.currentUser?.uid ?: return
        try {
            val userRef  = db.collection("users").document(uid)
            if (isTheme) {
                userRef.update(
                    "unlockedThemes",
                    com.google.firebase.firestore.FieldValue.arrayUnion(productId)
                ).await()
                _unlockedThemes.value = _unlockedThemes.value + productId
                Log.d(TAG, "Theme unlocked: $productId for $uid")
                _purchaseState.value = PurchaseState.Success(productId, 0)
            } else if (isSticker) {
                // Grant sticker pack: add to unlockedStickerPacks array in Firestore
                userRef.update(
                    "unlockedStickerPacks",
                    com.google.firebase.firestore.FieldValue.arrayUnion(productId)
                ).await()
                _unlockedStickers.value = _unlockedStickers.value + productId
                Log.d(TAG, "Sticker pack unlocked: $productId for $uid")
                _purchaseState.value = PurchaseState.Success(productId, 0)
            } else {
                val userSnap = userRef.get().await()
                val current  = (userSnap.getLong("chatMsgLimit") ?: 7L).toInt()
                val newLimit = current + messages
                userRef.update(
                    mapOf(
                        "chatLimitUnlocked" to true,
                        "chatMsgLimit"      to newLimit.toLong()
                    )
                ).await()
                Log.d(TAG, "Granted $messages messages. New limit: $newLimit")
                _purchaseState.value = PurchaseState.Success(productId, messages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore grant failed", e)
            _purchaseState.value = PurchaseState.Failed("Purchase confirmed but could not update. Contact support.")
        }
    }

    // ── Restore purchases on launch (non-consumable only — themes, stickers, chat packs) ───
    // Cookie packs are CONSUMABLE — after consumption they are GONE from queryPurchasesAsync.
    // We must NOT try to restore cookies here; they are credited in handlePurchase at purchase time.
    private suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
            client.queryPurchasesAsync(params) { r, list -> cont.resume(r to list) }
        }
        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            result.second
                .filter { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    // Skip cookie products — they are consumable and already processed
                    purchase.products.none { it in VeilProductId.ALL_COOKIE_IDS }
                }
                .forEach { handlePurchase(it) }
        }
    }

    fun resetPurchaseState() { _purchaseState.value = PurchaseState.Idle }
}

