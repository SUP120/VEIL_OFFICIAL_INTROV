package com.ns.findyourintroverfriend.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────────────────────────
//  ⚠️  REQUIRED SETUP — do this before the app will sign in:
//
//  1. Go to Firebase Console → your project → Project Settings → General
//  2. Under "Your apps" → Android app → SHA certificate fingerprints
//  3. Add your DEBUG SHA-1 (run: ./gradlew signingReport in the terminal)
//  4. Re-download google-services.json and place it in app/
//  5. Firebase Console → Authentication → Sign-in Method → Google → Enable
//  6. Copy the Web client ID from:
//     Firebase Console → Authentication → Sign-in Method → Google
//     → Web SDK configuration → Web client ID
//  7. Paste it below, replacing the placeholder
// ─────────────────────────────────────────────────────────────────────────────
private const val WEB_CLIENT_ID = "202022157666-eeh2pb6u4cbtvibviaptoqmi960t1usg.apps.googleusercontent.com"

private const val TAG = "VeilAuth"

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────
sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val needsProfile: Boolean) : AuthUiState()
    data class Error(val message: String, val code: Int = -1) : AuthUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    // ── Build GoogleSignInClient ───────────────────────────────────────────────
    fun buildGoogleSignInClient(context: Context): GoogleSignInClient {
        // Sign out first so the account picker always appears (no silent re-auth issues)
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    // ── Handle ApiException from the launcher ─────────────────────────────────
    fun onGoogleSignInError(statusCode: Int) {
        Log.e(TAG, "Google Sign-In ApiException statusCode=$statusCode")
        val msg = when (statusCode) {
            12501 -> "Sign-in was cancelled. Tap the button to try again."
            12502 -> "Sign-in network error. Please check your internet connection."
            10    -> "Configuration error (code 10).\n\nPlease ensure:\n" +
                     "• SHA-1 fingerprint is added in Firebase Console\n" +
                     "• google-services.json is in the app/ folder\n" +
                     "• Google Sign-In is enabled in Firebase Auth"
            7     -> "No internet connection. Please check your network."
            else  -> "Sign-in failed (code $statusCode). Please try again."
        }
        _state.value = AuthUiState.Error(message = msg, code = statusCode)
    }

    // ── Main sign-in flow ─────────────────────────────────────────────────────
    fun onGoogleSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).await()

                val firebaseUser = auth.currentUser
                    ?: throw Exception("No user after sign-in.")
                val uid = firebaseUser.uid

                // ── Persist Google profile data to Firestore ──────────────────
                // photoUrl is the canonical Google profile photo URL.
                // We merge so we never overwrite a custom Cloudinary photo that
                // a returning user may have uploaded during profile setup.
                val googlePhotoUrl   = account.photoUrl?.toString()
                    ?: firebaseUser.photoUrl?.toString()
                    ?: ""
                val googleName       = account.displayName
                    ?: firebaseUser.displayName
                    ?: ""

                val userRef = db.collection("users").document(uid)
                val docSnap = try { userRef.get().await() } catch (_: Exception) { null }

                val updates = mutableMapOf<String, Any>()
                // Only set photoUrl if doc doesn't have a non-Google one already
                val existingPhoto = docSnap?.getString("photoUrl") ?: ""
                if (existingPhoto.isBlank() && googlePhotoUrl.isNotBlank()) {
                    updates["photoUrl"] = googlePhotoUrl
                }
                // Always refresh displayName from Google in case it changed
                if (googleName.isNotBlank()) {
                    updates["displayName"] = googleName
                }
                updates["lastSignIn"] = System.currentTimeMillis()
                updates["uid"]        = uid

                if (updates.isNotEmpty()) {
                    try { userRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await() }
                    catch (e: Exception) { Log.w(TAG, "Failed to merge user doc: ${e.message}") }
                }

                // ── Check if profile setup is complete ────────────────────────
                val isComplete = try {
                    val profileDoc = userRef.get().await()
                    profileDoc.exists() && profileDoc.getBoolean("profileComplete") == true
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read profile doc — treating as new", e)
                    false
                }

                Log.d(TAG, "Signed in uid=$uid profileComplete=$isComplete photoUrl=$googlePhotoUrl")
                _state.value = AuthUiState.Success(needsProfile = !isComplete)

            } catch (e: Exception) {
                Log.e(TAG, "Firebase credential sign-in failed", e)
                _state.value = AuthUiState.Error(
                    message = e.localizedMessage ?: "Authentication failed.",
                    code    = -1
                )
            }
        }
    }

    fun resetState() { _state.value = AuthUiState.Idle }

    // ── Reviewer bypass: Email/Password sign-in (hidden from normal users) ────────
    // Triggered by long-pressing the Terms link on AuthScreen.
    // Credentials are provided in Play Console App Access instructions only.
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val uid = auth.currentUser?.uid
                    ?: throw Exception("No user after email sign-in")
                val doc      = db.collection("users").document(uid).get().await()
                val complete = doc.exists() && doc.getBoolean("profileComplete") == true
                _state.value = AuthUiState.Success(needsProfile = !complete)
            } catch (e: Exception) {
                Log.e(TAG, "Reviewer email sign-in failed", e)
                _state.value = AuthUiState.Error(
                    message = "Invalid reviewer credentials.",
                    code    = -1
                )
            }
        }
    }

    fun currentUserSignedIn() = auth.currentUser != null

    fun checkExistingSession() {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val doc      = db.collection("users").document(user.uid).get().await()
                val complete = doc.exists() && doc.getBoolean("profileComplete") == true
                _state.value = AuthUiState.Success(needsProfile = !complete)
            } catch (e: Exception) {
                Log.w(TAG, "Session check failed — treating as not logged in", e)
                _state.value = AuthUiState.Idle   // show login screen again safely
            }
        }
    }
}
