package com.example.myapplication.ui.screens.onboarding

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.ui.screens.addclothing.BatchAddFlow
import com.example.myapplication.ui.screens.addclothing.BatchAddViewModel
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkLight
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val aestheticTags = listOf(
    "Minimal", "Classic", "Streetwear", "Romantic",
    "Edgy", "Sporty", "Vintage", "Preppy", "Boho"
)

@Composable
fun OnboardingScreen(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository? = null,
    claudeApiService: ClaudeApiService? = null,
    apiKey: String = "",
    authRepository: FirebaseAuthRepository? = null,
    onComplete: (styles: Set<String>) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedStyles by remember { mutableStateOf(setOf<String>()) }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState > initialState)
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            else
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
        },
        label = "onboarding_step"
    ) { currentStep ->
        when (currentStep) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> StyleStep(
                selected = selectedStyles,
                onToggle = { tag ->
                    selectedStyles = if (tag in selectedStyles) selectedStyles - tag else selectedStyles + tag
                },
                onNext = { step = 2 }
            )
            2 -> ProfileStep(
                profileRepository = profileRepository,
                onNext = { step = 3 }
            )
            3 -> PhotoStep(
                wardrobeRepository = wardrobeRepository,
                claudeApiService = claudeApiService,
                apiKey = apiKey,
                onNext = { step = 4 },
                onSkip = { step = 4 }
            )
            4 -> AuthStep(
                authRepository = authRepository,
                styles = selectedStyles,
                onComplete = { step = 5 }
            )
            else -> DoneStep(onComplete = { onComplete(selectedStyles) })
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E)),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MY",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 10.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                "CLOSET",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Style with intention.",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.5.sp
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .width(200.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0E0E0E))
        ) {
            Text("GET STARTED", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun StyleStep(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(72.dp))
            Text("Your", style = MaterialTheme.typography.titleLarge, color = Ash, fontWeight = FontWeight.Light)
            Text("aesthetic.", style = MaterialTheme.typography.displaySmall, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Choose all that resonate", style = MaterialTheme.typography.bodyMedium, color = Ash)
            Spacer(Modifier.height(32.dp))

            aestheticTags.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    row.forEach { tag ->
                        val isSel = tag in selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Ink else Color(0xFFF5F5F5))
                                .clickable { onToggle(tag) }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSel) Color.White else InkLight
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(total = 5, current = 0)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                Text("CONTINUE", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileStep(
    profileRepository: UserProfileRepository?,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var gender by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var bustText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }
    var hipsText by remember { mutableStateOf("") }
    var faceUri by remember { mutableStateOf<Uri?>(null) }
    var bodyUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    val faceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) faceUri = uri
    }
    val bodyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) bodyUri = uri
    }

    val canContinue = gender.isNotEmpty() && heightText.toIntOrNull() != null && weightText.toIntOrNull() != null

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(72.dp))
            Text("About", style = MaterialTheme.typography.titleLarge, color = Ash, fontWeight = FontWeight.Light)
            Text("you.", style = MaterialTheme.typography.displaySmall, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Helps tailor recommendations to your body", style = MaterialTheme.typography.bodyMedium, color = Ash)
            Spacer(Modifier.height(28.dp))

            // Gender — mandatory
            Text("GENDER", style = MaterialTheme.typography.labelSmall, color = Ash, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Male", "Female", "Other").forEach { g ->
                    val sel = gender == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Ink else Color(0xFFF5F5F5))
                            .clickable { gender = g }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            g,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) Color.White else InkLight
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Height & Weight — mandatory
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(Modifier.height(22.dp))

            // Photos — optional
            Text("PHOTOS  ·  optional", style = MaterialTheme.typography.labelSmall, color = Ash, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoPickerBox(
                    label = "Face",
                    uri = faceUri,
                    modifier = Modifier.weight(1f),
                    onClick = { faceLauncher.launch("image/*") }
                )
                PhotoPickerBox(
                    label = "Full Body",
                    uri = bodyUri,
                    modifier = Modifier.weight(1f),
                    onClick = { bodyLauncher.launch("image/*") }
                )
            }

            Spacer(Modifier.height(22.dp))

            // Measurements — optional
            Text("MEASUREMENTS (cm)  ·  optional", style = MaterialTheme.typography.labelSmall, color = Ash, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("Bust", bustText, { v: String -> bustText = v }),
                    Triple("Waist", waistText, { v: String -> waistText = v }),
                    Triple("Hips", hipsText, { v: String -> hipsText = v })
                ).forEach { (label, value, setter) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { setter(it.filter { c -> c.isDigit() }.take(3)) },
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(total = 5, current = 1)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (profileRepository == null) { onNext(); return@Button }
                    saving = true
                    scope.launch {
                        val facePath = faceUri?.let { copyUriToFile(context, it, "face_${System.currentTimeMillis()}.jpg") } ?: ""
                        val bodyPath = bodyUri?.let { copyUriToFile(context, it, "body_${System.currentTimeMillis()}.jpg") } ?: ""
                        val existing = profileRepository.getProfileOnce() ?: UserProfile()
                        profileRepository.saveProfile(
                            existing.copy(
                                gender = gender,
                                height = heightText.toIntOrNull() ?: 0,
                                weight = weightText.toIntOrNull() ?: 0,
                                bust = bustText.toIntOrNull() ?: existing.bust,
                                waist = waistText.toIntOrNull() ?: existing.waist,
                                hips = hipsText.toIntOrNull() ?: existing.hips,
                                faceImagePath = facePath.ifEmpty { existing.faceImagePath },
                                bodyImagePath = bodyPath.ifEmpty { existing.bodyImagePath }
                            )
                        )
                        saving = false
                        onNext()
                    }
                },
                enabled = canContinue && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("CONTINUE", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun PhotoPickerBox(
    label: String,
    uri: Uri?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF5F5F5))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun PhotoStep(
    wardrobeRepository: WardrobeRepository,
    claudeApiService: ClaudeApiService?,
    apiKey: String,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val batchVm: BatchAddViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            BatchAddViewModel(wardrobeRepository, claudeApiService, apiKey) as T
    })

    BatchAddFlow(
        viewModel = batchVm,
        onDone = { onNext() },
        onSkip = onSkip,
        stepIndicator = { StepDots(total = 5, current = 2) }
    )
}

@Composable
private fun AuthStep(
    authRepository: FirebaseAuthRepository?,
    styles: Set<String>,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(72.dp))
            Text("Create your", style = MaterialTheme.typography.titleLarge, color = Ash, fontWeight = FontWeight.Light)
            Text("account.", style = MaterialTheme.typography.displaySmall, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Sync your wardrobe across devices", style = MaterialTheme.typography.bodyMedium, color = Ash)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Display Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    TextButton(onClick = { showPw = !showPw }) {
                        Text(if (showPw) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCC0000))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(total = 5, current = 3)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        val repo = authRepository
                        if (repo != null && email.isNotBlank() && password.isNotBlank()) {
                            repo.createAccount(email.trim(), password, name.trim())
                                .onSuccess { onComplete() }
                                .onFailure { e ->
                                    loading = false
                                    error = when {
                                        "already in use" in (e.message ?: "") -> "Email already registered"
                                        "least 6" in (e.message ?: "") -> "Password must be at least 6 characters"
                                        else -> e.message ?: "Sign up failed"
                                    }
                                }
                        } else {
                            repo?.signInAnonymously()
                            onComplete()
                        }
                    }
                },
                enabled = !loading && (authRepository == null ||
                    (email.isNotBlank() && password.isNotBlank() && name.isNotBlank())),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("CREATE ACCOUNT", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = {
                scope.launch {
                    loading = true
                    authRepository?.signInAnonymously()
                    onComplete()
                }
            }) {
                Text("Continue as Guest →", style = MaterialTheme.typography.labelMedium, color = Ash)
            }
        }
    }
}

@Composable
private fun DoneStep(onComplete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 28.dp)
                .padding(bottom = 140.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(20.dp))
            Text("All set.", style = MaterialTheme.typography.displaySmall, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your wardrobe is ready.", style = MaterialTheme.typography.bodyLarge, color = Ash)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(total = 5, current = 4)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                Text("ENTER", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val w by animateDpAsState(
                targetValue = if (i == current) 24.dp else 6.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotW"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == current) Ink else Color(0xFFE0E0E0))
            )
        }
    }
}

private fun copyUriToFile(context: Context, uri: Uri, fileName: String): String = try {
    val dir = File(context.filesDir, "profile").apply { mkdirs() }
    val dest = File(dir, fileName)
    context.contentResolver.openInputStream(uri)?.use { inp ->
        FileOutputStream(dest).use { out -> inp.copyTo(out) }
    }
    dest.absolutePath
} catch (e: Exception) { "" }
