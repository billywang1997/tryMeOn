package com.trymeon.app.data.auth

import android.content.Context
import android.content.Intent
import com.trymeon.app.domain.model.AppUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: Flow<AppUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAppUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUserSnapshot: AppUser? get() = auth.currentUser?.toAppUser()

    // ── Anonymous ────────────────────────────────────────────────────────

    suspend fun signInAnonymously(): Result<AppUser> = runCatching {
        auth.signInAnonymously().await().user!!.toAppUser()
    }

    // ── Email / Password ─────────────────────────────────────────────────

    suspend fun createAccount(email: String, password: String, displayName: String): Result<AppUser> = runCatching {
        val result = if (auth.currentUser?.isAnonymous == true) {
            // Upgrade anonymous → email account (preserves UID)
            val credential = EmailAuthProvider.getCredential(email, password)
            auth.currentUser!!.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        val user = result.user!!
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        user.reload().await()
        auth.currentUser!!.toAppUser()
    }

    suspend fun signIn(email: String, password: String): Result<AppUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user!!.toAppUser()
    }

    // ── Google ───────────────────────────────────────────────────────────

    fun getGoogleSignInIntent(webClientId: String): Intent {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    suspend fun signInWithGoogle(idToken: String): Result<AppUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = if (auth.currentUser?.isAnonymous == true) {
            auth.currentUser!!.linkWithCredential(credential).await()
        } else {
            auth.signInWithCredential(credential).await()
        }
        result.user!!.toAppUser()
    }

    // ── Account management ───────────────────────────────────────────────

    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        auth.currentUser!!.updateProfile(request).await()
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.currentUser!!.updatePassword(newPassword).await()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() {
        auth.signOut()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun FirebaseUser.toAppUser() = AppUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isAnonymous = isAnonymous
    )
}
