# 📄 Payment Verification & Tracking System (Android + Firebase)

## 🎯 Objective

Design a **secure, reliable, and unified payment system** using
Google Play Billing Library +
Firebase Cloud Functions

Ensure:

* No fake purchases
* No duplicate credits
* Full tracking of success / failed / cancelled
* Works for:

  * One-time purchases
  * Credit packs
  * Subscriptions

---

# 🧱 Core Principles (MANDATORY RULES)

## 1. NEVER TRUST CLIENT

* The Android app MUST NOT grant credits or unlock features.
* All purchases MUST be verified via backend.

## 2. PURCHASE TOKEN IS PRIMARY KEY

* Every purchase is uniquely identified by `purchaseToken`
* It must be stored and never reused

## 3. SERVER IS SOURCE OF TRUTH

* Only backend decides:

  * Success
  * Failure
  * Cancellation
  * Refund

---

# ⚙️ SYSTEM ARCHITECTURE

```text
Android App
   ↓
Google Play Billing
   ↓
Send purchaseToken → Firebase Cloud Function
   ↓
Verify via Google Play Developer API
   ↓
Firestore (update credits + logs)
```

---

# 🔄 PURCHASE FLOW (STRICT)

## STEP 1: User Initiates Purchase

* App calls BillingClient
* Product is launched

---

## STEP 2: Purchase Response (Client)

App receives:

* purchaseToken
* productId
* purchaseState

### ACTION:

* Immediately send data to backend
* DO NOT grant credits

```json
POST /verifyPurchase
{
  userId,
  purchaseToken,
  productId
}
```

---

## STEP 3: Backend Verification

Use:
Google Play Developer API

Check:

| Field            | Condition    |
| ---------------- | ------------ |
| purchaseState    | PURCHASED    |
| consumptionState | NOT_CONSUMED |
| orderId          | exists       |

---

## STEP 4: Decision Engine

### CASE 1: VALID PURCHASE

* Add credits / unlock feature
* Mark purchase as SUCCESS
* Mark token as used

---

### CASE 2: INVALID PURCHASE

* Mark as FAILED
* Do not grant anything

---

### CASE 3: DUPLICATE TOKEN

* Reject immediately
* Prevent double credit

---

# 🧾 DATABASE DESIGN (Firestore)

## Collection: `purchases`

| Field         | Type      | Description                  |
| ------------- | --------- | ---------------------------- |
| userId        | string    | buyer                        |
| productId     | string    | item                         |
| purchaseToken | string    | unique                       |
| status        | string    | SUCCESS / FAILED / CANCELLED |
| creditsGiven  | boolean   | true/false                   |
| createdAt     | timestamp |                              |

---

## Collection: `users`

| Field              | Type             |
| ------------------ | ---------------- |
| credits            | number           |
| subscriptionStatus | active / expired |

---

# 📊 STATUS TRACKING LOGIC

## SUCCESS

* Verified via Google API
* Credits given

## FAILED

* API verification fails
* Payment invalid

## CANCELLED (CLIENT SIDE)

* User cancels purchase UI
* No backend entry required unless tracked

## REFUNDED / REVOKED (ADVANCED)

Use:
Google Play Real-time Developer Notifications

* Updates backend when:

  * Refund issued
  * Subscription cancelled
  * Chargeback

---

# 💳 PRODUCT TYPES HANDLING

## 1. ONE-TIME PURCHASE

* Verify once
* Grant permanent unlock

---

## 2. CREDIT PURCHASE

* Add credits after verification
* Ensure token not reused

---

## 3. SUBSCRIPTIONS

Additional checks:

* expiryTimeMillis
* autoRenewing

### Logic:

* Active → allow premium
* Expired → revoke access

---

# 🔁 DUPLICATE PROTECTION (CRITICAL)

Before processing:

```js
if (purchaseToken exists in DB) {
   reject request;
}
```

---

# 🧪 TESTING SYSTEM

Use:
Google Play License Testing

Test scenarios:

* Success
* Failure
* Cancel
* Retry purchase

---

# 🚨 FAILURE HANDLING

| Scenario                | Action                   |
| ----------------------- | ------------------------ |
| App crash after payment | Recover via backend logs |
| Network failure         | Retry verification       |
| Duplicate requests      | Ignore using token       |

---

# 🔐 SECURITY RULES

* Firebase function must validate:

  * authenticated user
  * valid request format

* Never expose:

  * API keys
  * verification logic to client

---

# 📈 OPTIONAL IMPROVEMENTS

* Admin dashboard (view purchases)
* Retry queue for failed verifications
* Analytics on conversion rates

---

# 🎯 FINAL GUARANTEES

If implemented correctly:

✅ No fake purchases
✅ No duplicate credits
✅ Full audit trail
✅ Works at scale
✅ Zero dependency on client trust

---

# 📌 IMPLEMENTATION PRIORITY

1. Billing integration
2. Backend verification
3. Firestore logging
4. Credit system
5. Duplicate protection

---

END OF DOCUMENT
