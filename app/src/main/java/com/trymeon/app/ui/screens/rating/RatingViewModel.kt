package com.trymeon.app.ui.screens.rating

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class RatingUiState(
    val imageUri: Uri? = null,
    val result: String = "",
    val isLoading: Boolean = false,
    val error: String = "",
    val profile: UserProfile? = null
)

data class OutfitScore(
    val color: Int = 0,
    val occasion: Int = 0,
    val overall: Int = 0,
    val highlight: String = "",
    val suggestion: String = ""
)

class RatingViewModel(
    private val claudeService: ClaudeApiService,
    private val profileRepository: UserProfileRepository,
    private val apiKey: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(RatingUiState())
    val uiState: StateFlow<RatingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.getProfile().collect { p -> _uiState.value = _uiState.value.copy(profile = p) }
        }
    }

    fun setImage(uri: Uri) { _uiState.value = _uiState.value.copy(imageUri = uri, result = "", error = "") }

    fun rate(context: Context) {
        val uri = _uiState.value.imageUri ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, error = "", result = "")
        viewModelScope.launch {
            try {
                val b64 = encodeUri(context, uri) ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Unable to read image")
                    return@launch
                }
                val result = claudeService.rateOutfit(apiKey, b64, _uiState.value.profile)
                _uiState.value = _uiState.value.copy(result = result, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Something went wrong", isLoading = false)
            }
        }
    }

    private suspend fun encodeUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bm = BitmapFactory.decodeStream(stream)
            val ratio = 512f / maxOf(bm.width, bm.height)
            val resized = if (ratio < 1f)
                Bitmap.createScaledBitmap(bm, (bm.width * ratio).toInt(), (bm.height * ratio).toInt(), true)
            else bm
            val out = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}

fun parseScore(result: String): OutfitScore {
    fun extract(key: String) = Regex("$key[:](\\d+)").find(result)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    fun extractText(key: String) = Regex("$key[:](.+)").find(result)?.groupValues?.get(1)?.trim() ?: ""
    return OutfitScore(
        color = extract("Color Harmony"),
        occasion = extract("Occasion Fit"),
        overall = extract("Overall Look"),
        highlight = extractText("Highlight"),
        suggestion = extractText("Suggestion")
    )
}
