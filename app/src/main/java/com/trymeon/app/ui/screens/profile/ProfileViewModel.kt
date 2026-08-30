package com.trymeon.app.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.auth.FirebaseAuthRepository
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.AppUser
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.SavedImage
import com.trymeon.app.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileViewModel(
    private val profileRepository: UserProfileRepository,
    private val wardrobeRepository: WardrobeRepository,
    // Null when there is no cloud. Everything else on this screen — body,
    // saved looks, price expectation, style — is local and must still work.
    private val authRepository: FirebaseAuthRepository? = null,
    private val firestoreRepository: FirestoreRepository? = null,
) : ViewModel() {

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    /** Whether an account can be created or signed into at all. */
    val cloudAvailable: Boolean = authRepository != null

    val currentUser: StateFlow<AppUser?> =
        (authRepository?.currentUser ?: flowOf(null)).stateIn(
            viewModelScope, SharingStarted.Eagerly, authRepository?.currentUserSnapshot
        )

    val savedImages: StateFlow<List<SavedImage>> = wardrobeRepository.getSavedImages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wardrobeItems: StateFlow<List<ClothingItem>> = wardrobeRepository.getAllClothing()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteSavedImage(imageId: Long) {
        viewModelScope.launch { wardrobeRepository.deleteSavedImage(imageId) }
    }

    init {
        viewModelScope.launch {
            profileRepository.getProfile().collect { p ->
                _profile.value = p ?: UserProfile()
            }
        }
    }

    fun updateProfile(profile: UserProfile) { _profile.value = profile }

    fun saveProfile() {
        viewModelScope.launch {
            // UserProfileRepository handles photo upload + Firestore sync internally.
            profileRepository.saveProfile(_profile.value)
            _saved.value = true
        }
    }

    fun savedAcknowledged() { _saved.value = false }
    fun clearAuthError() { _authError.value = null }

    fun savePhoto(context: Context, uri: Uri, isFace: Boolean) {
        viewModelScope.launch {
            val localPath = saveImageToInternal(context, uri, if (isFace) "face" else "body") ?: return@launch
            val updated = if (isFace) _profile.value.copy(faceImagePath = localPath)
                         else _profile.value.copy(bodyImagePath = localPath)
            _profile.value = updated
            // saveProfile uploads the local photo to Storage and syncs to Firestore.
            profileRepository.saveProfile(updated)
        }
    }

    // ── Auth operations ──────────────────────────────────────────────────

    fun createAccount(email: String, password: String, displayName: String, styles: Set<String> = emptySet()) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val auth = authRepository ?: return@launch offline()
            val result = auth.createAccount(email, password, displayName)
            result.onSuccess { user ->
                firestoreRepository?.saveUserMeta(user.uid, user.displayName, user.email, false, styles)
                firestoreRepository?.saveProfile(user.uid, _profile.value)
            }
            result.onFailure { _authError.value = friendlyError(it.message) }
            _authLoading.value = false
        }
    }

    fun signIn(email: String, password: String) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val auth = authRepository ?: return@launch offline()
            val result = auth.signIn(email, password)
            result.onFailure { _authError.value = friendlyError(it.message) }
            _authLoading.value = false
        }
    }

    fun signInWithGoogle(idToken: String, styles: Set<String> = emptySet()) {
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val auth = authRepository ?: return@launch offline()
            val result = auth.signInWithGoogle(idToken)
            result.onSuccess { user ->
                firestoreRepository?.saveUserMeta(user.uid, user.displayName, user.email, false, styles)
            }
            result.onFailure { _authError.value = friendlyError(it.message) }
            _authLoading.value = false
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            (authRepository ?: return@launch offline()).updateDisplayName(name)
                .onFailure { _authError.value = friendlyError(it.message) }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            (authRepository ?: return@launch offline()).updatePassword(newPassword)
                .onSuccess { _saved.value = true }
                .onFailure { _authError.value = friendlyError(it.message) }
        }
    }

    fun sendPasswordReset() {
        val email = currentUser.value?.email ?: return
        viewModelScope.launch {
            (authRepository ?: return@launch offline()).sendPasswordReset(email)
                .onSuccess { _authError.value = "Reset email sent to $email" }
                .onFailure { _authError.value = friendlyError(it.message) }
        }
    }

    fun signOut() {
        authRepository?.signOut()
    }

    /** Says why nothing happened, instead of appearing to hang. */
    private fun offline() {
        _authError.value = "Accounts are unavailable on this build"
        _authLoading.value = false
    }

    private fun friendlyError(msg: String?): String = when {
        msg == null -> "Something went wrong"
        "email address is already in use" in msg -> "This email is already registered"
        "password is invalid" in msg || "wrong-password" in msg -> "Incorrect password"
        "no user record" in msg || "user-not-found" in msg -> "No account found with this email"
        "email address is badly formatted" in msg -> "Invalid email address"
        "password should be at least" in msg -> "Password must be at least 6 characters"
        "network" in msg.lowercase() -> "Network error — check your connection"
        else -> msg
    }

    private fun saveImageToInternal(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) { null }
    }
}
