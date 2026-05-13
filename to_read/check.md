1. Core System You Need

You will use:

Google Play Console → create products
Google Play Billing Library → handle payments in app
Firebase → store user cookies safely
2. Step-by-Step Architecture (IMPORTANT)
Flow:
User clicks "Buy 50 Cookies (₹30)"
        ↓
Google Play Billing opens
        ↓
User pays
        ↓
Google returns PURCHASE TOKEN
        ↓
Your app verifies purchase
        ↓
Grant cookies (ONLY after verification)
3. Create Products in Play Console

Go to:

👉 Monetization → Products → In-app products

Create:

ID: cookies_50
Price: ₹30
Type: One-time purchase (INAPP)

You can add multiple:

cookies_100
cookies_500

⚠️ Product ID must match exactly in your app.

4. Integrate Billing Library (Android)

Add dependency:

implementation 'com.android.billingclient:billing:6.0.1'
5. Initialize Billing Client
BillingClient billingClient = BillingClient.newBuilder(context)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases()
    .build();
6. Handle Purchase Result (CRITICAL PART)
PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
        for (Purchase purchase : purchases) {
            handlePurchase(purchase);
        }
    }
};
7. Verify & Grant Cookies
void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {

        if (!isPurchaseAcknowledged(purchase)) {
            grantCookies(purchase);
            acknowledgePurchase(purchase);
        }
    }
}
8. Add Cookies ONLY After Verification
void grantCookies(Purchase purchase) {
    String productId = purchase.getProducts().get(0);

    if (productId.equals("cookies_50")) {
        addCookiesToUser(50);
    }
}
9. VERY IMPORTANT (Security)

If you skip this → users can hack your app.

Minimum safety:
Check purchaseState == PURCHASED
Check purchase.isAcknowledged()
BEST PRACTICE (Recommended):

Verify purchase on backend using:

👉 Google Play Developer API

Flow:

App → send purchaseToken to Firebase
Firebase → verify with Google API
If valid → update cookies
10. Acknowledge Purchase (MANDATORY)
AcknowledgePurchaseParams params =
    AcknowledgePurchaseParams.newBuilder()
        .setPurchaseToken(purchase.getPurchaseToken())
        .build();

billingClient.acknowledgePurchase(params, billingResult -> {});

⚠️ If you don’t acknowledge:

Google will refund automatically
11. Handle Edge Cases

You MUST handle:

✔ Success

→ Add cookies

✔ Failed

→ Show "Payment failed"

✔ Cancelled

→ Show "Payment cancelled"

✔ Pending

→ Show "Processing..."

12. Restore Purchases (Important)

When user opens app again:

billingClient.queryPurchasesAsync(...)

So users don’t lose purchases.

13. Firebase Structure (Recommended)
users/
  userId/
    cookies: 150
    purchases/
       purchaseToken123: true

This prevents duplicate credit.

14. Common Mistakes (Avoid These)

❌ Giving cookies before verification
❌ Not acknowledging purchase
❌ Not handling duplicate purchases
❌ Testing without Play Console upload

15. Testing (Most People Fail Here)

You MUST:

Upload app to Internal Testing
Add tester Gmail accounts
Use test payments
Final Architecture Summary
Google Play Billing → Purchase Token → Verify → Add Cookies → Save in Firebase