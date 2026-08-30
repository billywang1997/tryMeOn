package com.trymeon.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Who the signed-in user is, when there is a cloud at all.
 *
 * Cloud sync is an optional convenience: the wardrobe, the wishlist and the
 * profile all live locally and work without an account. But
 * `FirebaseAuth.getInstance()` throws rather than returning null when Firebase
 * has not been configured or could not start, so every caller that merely
 * wanted to know "am I signed in" crashed instead of hearing "no".
 *
 * Asking through here answers no, which is the truthful answer and the one the
 * repositories are already written to handle.
 */
object CloudIdentity {

    /** The signed-in user, or null when there is no cloud and no session. */
    fun currentUser(): FirebaseUser? =
        runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    /** The current uid, or null when there is no cloud and no session. */
    fun uid(): String? = currentUser()?.uid

    /** The auth instance, or null when Firebase never started. */
    fun auth(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    /** Whether cloud-backed features can run at all. */
    val available: Boolean get() = runCatching { FirebaseAuth.getInstance() }.isSuccess
}
