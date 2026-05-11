package com.example.myapplication.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.AppSettings
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.AppUser
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.SavedImage
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.ui.components.FashionImage
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkLight
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject

@Composable
fun ProfileScreen(
    repository: UserProfileRepository,
    wardrobeRepository: WardrobeRepository,
    authRepository: FirebaseAuthRepository,
    firestoreRepository: FirestoreRepository,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val vm: ProfileViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repository, wardrobeRepository, authRepository, firestoreRepository) as T
    })

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showAmazonDialog by remember { mutableStateOf(false) }
    var showRapidApiDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf<AuthDialogMode?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val profile by vm.profile.collectAsState()
    val saved by vm.saved.collectAsState()
    val authError by vm.authError.collectAsState()
    val authLoading by vm.authLoading.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val savedImages by vm.savedImages.collectAsState()
    val wardrobeItems by vm.wardrobeItems.collectAsState()

    var selectedLook by remember { mutableStateOf<SavedImage?>(null) }

    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var height by remember(profile.height) { mutableStateOf(if (profile.height > 0) profile.height.toString() else "") }
    var weight by remember(profile.weight) { mutableStateOf(if (profile.weight > 0) profile.weight.toString() else "") }
    var bust   by remember(profile.bust)   { mutableStateOf(if (profile.bust > 0) profile.bust.toString() else "") }
    var waist  by remember(profile.waist)  { mutableStateOf(if (profile.waist > 0) profile.waist.toString() else "") }
    var hips   by remember(profile.hips)   { mutableStateOf(if (profile.hips > 0) profile.hips.toString() else "") }

    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.savePhoto(context, uri, isFace = true)
    }
    val bodyPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.savePhoto(context, uri, isFace = false)
    }

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            account.idToken?.let { vm.signInWithGoogle(it, settings.styleKeywords) }
        } catch (_: ApiException) {}
    }

    if (saved) { LaunchedEffect(saved) { vm.savedAcknowledged() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PROFILE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
            TextButton(
                onClick = {
                    vm.updateProfile(UserProfile(
                        gender = gender,
                        height = height.toIntOrNull() ?: 0,
                        weight = weight.toIntOrNull() ?: 0,
                        bust   = bust.toIntOrNull() ?: 0,
                        waist  = waist.toIntOrNull() ?: 0,
                        hips   = hips.toIntOrNull() ?: 0,
                        faceImagePath = profile.faceImagePath,
                        bodyImagePath = profile.bodyImagePath
                    ))
                    vm.saveProfile()
                }
            ) {
                Text("SAVE", style = MaterialTheme.typography.labelLarge, color = Ink)
            }
        }
        HorizontalDivider(color = Mist)

        Spacer(Modifier.height(20.dp))

        // ── Account card ─────────────────────────────────────────────────
        AccountCard(
            user = currentUser,
            authLoading = authLoading,
            onSignIn = { showAuthDialog = AuthDialogMode.SIGN_IN },
            onCreateAccount = { showAuthDialog = AuthDialogMode.CREATE },
            onSignOut = { vm.signOut() },
            onEditName = { showEditNameDialog = true },
            onChangePassword = { showChangePasswordDialog = true },
            onGoogleSignIn = {
                if (settings.googleWebClientId.isNotBlank()) {
                    val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(settings.googleWebClientId)
                        .requestEmail()
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, opts).signInIntent)
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(24.dp))

        // Photos
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfilePhotoSlot("Face Photo", profile.faceImagePath, { facePicker.launch("image/*") }, Modifier.weight(1f))
            ProfilePhotoSlot("Full Body Photo", profile.bodyImagePath, { bodyPicker.launch("image/*") }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))

        ProfileSection("Gender") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Female", "Male", "Other").forEach { g ->
                    val sel = g == gender
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Ink else Paper)
                            .border(1.dp, if (sel) Ink else Mist, RoundedCornerShape(8.dp))
                            .clickable { gender = g }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(g, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else InkLight)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        ProfileSection("Body") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CleanNumberField(height, { height = it }, "Height", "cm", Modifier.weight(1f))
                CleanNumberField(weight, { weight = it }, "Weight", "kg", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(20.dp))

        ProfileSection("Measurements (cm)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CleanNumberField(bust,  { bust = it },  "Bust",  "", Modifier.weight(1f))
                CleanNumberField(waist, { waist = it }, "Waist", "", Modifier.weight(1f))
                CleanNumberField(hips,  { hips = it },  "Hips",  "", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(20.dp))

        // My Looks gallery
        if (savedImages.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Looks", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("${savedImages.size} saved", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
                Spacer(Modifier.height(12.dp))
                val rows = savedImages.chunked(3)
                rows.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        rowItems.forEach { img ->
                            SavedLookCard(
                                image = img,
                                onDelete = { vm.deleteSavedImage(img.id) },
                                onClick = { selectedLook = img },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { showApiKeyDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkLight)
            ) {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit OpenAI Key", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { showAmazonDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkLight)
            ) {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (settings.amazonAssociateTag.isNotBlank()) "Amazon PA-API  ✓" else "Edit Amazon PA-API Keys",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedButton(
                onClick = { showRapidApiDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkLight)
            ) {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (settings.rapidApiKey.isNotBlank()) "RapidAPI Key  ✓" else "Edit RapidAPI Key (ASOS / The Iconic)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    showAuthDialog?.let { mode ->
        AuthDialog(
            mode = mode,
            loading = authLoading,
            error = authError,
            onDismiss = { showAuthDialog = null; vm.clearAuthError() },
            onSubmit = { email, password, name ->
                if (mode == AuthDialogMode.CREATE) vm.createAccount(email, password, name, settings.styleKeywords)
                else vm.signIn(email, password)
                showAuthDialog = null
            }
        )
    }

    if (showEditNameDialog) {
        EditNameDialog(
            current = currentUser?.displayName ?: "",
            onDismiss = { showEditNameDialog = false },
            onConfirm = { name -> vm.updateDisplayName(name); showEditNameDialog = false }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { pw -> vm.updatePassword(pw); showChangePasswordDialog = false },
            onReset = { vm.sendPasswordReset(); showChangePasswordDialog = false }
        )
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            current = settings.claudeApiKey,
            onDismiss = { showApiKeyDialog = false },
            onConfirm = { key -> settings.claudeApiKey = key; showApiKeyDialog = false }
        )
    }

    if (showRapidApiDialog) {
        RapidApiKeyDialog(
            current = settings.rapidApiKey,
            onDismiss = { showRapidApiDialog = false },
            onConfirm = { key -> settings.rapidApiKey = key; showRapidApiDialog = false }
        )
    }

    if (showAmazonDialog) {
        AmazonKeysDialog(
            accessKey = settings.amazonAccessKey,
            secretKey = settings.amazonSecretKey,
            associateTag = settings.amazonAssociateTag,
            onDismiss = { showAmazonDialog = false },
            onConfirm = { ak, sk, tag ->
                settings.amazonAccessKey = ak; settings.amazonSecretKey = sk
                settings.amazonAssociateTag = tag; showAmazonDialog = false
            }
        )
    }

    selectedLook?.let { look ->
        val allItems = remember(wardrobeItems) { wardrobeItems + BasicWardrobeProvider.items }
        LookDetailSheet(
            image = look,
            allItems = allItems,
            onDelete = { vm.deleteSavedImage(look.id); selectedLook = null },
            onDismiss = { selectedLook = null }
        )
    }

    if (authError != null) {
        LaunchedEffect(authError) {
            kotlinx.coroutines.delay(4000)
            vm.clearAuthError()
        }
    }

    if (saved || authError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.navigationBarsPadding().padding(bottom = 24.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (authError != null) MaterialTheme.colorScheme.error else Ink
            ) {
                Text(
                    authError ?: "Saved",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

// ── Account card ─────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    user: AppUser?,
    authLoading: Boolean,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignOut: () -> Unit,
    onEditName: () -> Unit,
    onChangePassword: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Paper,
        tonalElevation = 0.dp
    ) {
        if (authLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Ink)
            }
            return@Surface
        }

        if (user == null || user.isAnonymous) {
            // Guest state
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Mist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Ash, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Guest", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text("Your data is stored locally", style = MaterialTheme.typography.bodySmall, color = Ash)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCreateAccount,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)
                    ) { Text("Create Account", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = onSignIn,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Sign In", style = MaterialTheme.typography.labelMedium) }
                }
            }
        } else {
            // Signed-in state
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Avatar with initials
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.initials, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            user.displayName?.ifEmpty { "No name set" } ?: "No name set",
                            style = MaterialTheme.typography.titleMedium, color = Ink
                        )
                        if (!user.email.isNullOrEmpty()) {
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = Ash)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEditName,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Edit Name", style = MaterialTheme.typography.labelSmall) }
                    if (!user.email.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = onChangePassword,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Password", style = MaterialTheme.typography.labelSmall) }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Sign Out", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

// ── Auth dialog ───────────────────────────────────────────────────────────────

enum class AuthDialogMode { CREATE, SIGN_IN }

@Composable
private fun AuthDialog(
    mode: AuthDialogMode,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String, name: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == AuthDialogMode.CREATE) "CREATE ACCOUNT" else "SIGN IN", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == AuthDialogMode.CREATE) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Display Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                    )
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        TextButton(onClick = { showPw = !showPw }) {
                            Text(if (showPw) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    }
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(email.trim(), password, name.trim()) },
                enabled = email.isNotBlank() && password.isNotBlank() && !loading &&
                          (mode == AuthDialogMode.SIGN_IN || name.isNotBlank()),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (mode == AuthDialogMode.CREATE) "Create" else "Sign In")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}

@Composable
private fun EditNameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Display Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, onReset: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("New Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text("Confirm Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                )
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot password?", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 6 && password == confirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        content()
    }
}

@Composable
fun ProfilePhotoSlot(label: String, imagePath: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Paper)
                .border(1.dp, Mist, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (imagePath.isNotEmpty()) {
                AsyncImage(model = imagePath, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AddAPhoto, null, tint = Ash, modifier = Modifier.size(22.dp))
                    Text("Upload", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkLight)
    }
}

@Composable
fun CleanNumberField(value: String, onChange: (String) -> Unit, label: String, unit: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        suffix = if (unit.isNotEmpty()) {{ Text(unit, style = MaterialTheme.typography.labelSmall, color = Ash) }} else null,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier, shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun ApiKeyDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API KEY", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OpenAI / GPT-4o outfit analysis", style = MaterialTheme.typography.bodySmall, color = Ash)
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") },
                    placeholder = { Text("sk-...") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(key) }, enabled = key.isNotBlank(), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}

@Composable
fun RapidApiKeyDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var key by remember { mutableStateOf(current) }
    var showKey by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RAPIDAPI KEY", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Used for ASOS and The Iconic product search.\nGet a free key at rapidapi.com → subscribe to the ASOS and The Iconic APIs.",
                    style = MaterialTheme.typography.bodySmall, color = Ash)
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("RapidAPI Key") },
                    placeholder = { Text("xxxxxxxxxxxxxxxxxxxxxxxx...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(if (showKey) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(key) }, enabled = key.isNotBlank(), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}

@Composable
private fun SavedLookCard(
    image: SavedImage,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Paper)
            .clickable(onClick = onClick)
    ) {
        FashionImage(
            model = image.path,
            contentDescription = image.label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Scene label at bottom
        if (image.label.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text(image.label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
            }
        }
        // Delete button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .clickable { showConfirm = true },
            contentAlignment = Alignment.Center
        ) {
            Text("×", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove") },
            text = { Text("Remove this look from My Looks?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = Ash) } }
        )
    }
}

// ── Look detail full-screen dialog ───────────────────────────────────────────

private fun parseLookItemIds(note: String): List<Long> = try {
    val arr = JSONObject(note).optJSONArray("ids") ?: return emptyList()
    (0 until arr.length()).map { arr.getLong(it) }
} catch (_: Exception) { emptyList() }

private fun parseLookDate(note: String): String = try {
    JSONObject(note).optString("d", "")
} catch (_: Exception) { note.substringBefore(" ·").trim() }

@Composable
private fun LookDetailSheet(
    image: SavedImage,
    allItems: List<ClothingItem>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val itemIds = remember(image.note) { parseLookItemIds(image.note) }
    val date = remember(image.note) { parseLookDate(image.note) }
    val lookItems = remember(itemIds, allItems) {
        itemIds.mapNotNull { id -> allItems.find { it.id == id } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .systemBarsPadding()
            ) {
                // Full-width image
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.85f)) {
                    AsyncImage(
                        model = image.path,
                        contentDescription = image.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Info + items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Date + scene row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (image.label.isNotEmpty()) {
                                Text(image.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            if (date.isNotEmpty()) {
                                Text(date, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Items
                    if (lookItems.isNotEmpty()) {
                        Text(
                            "Items in this look",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(lookItems) { item ->
                                LookItemChip(item = item, context = context)
                            }
                        }
                    }
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(8.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LookItemChip(item: ClothingItem, context: android.content.Context) {
    val isEssential = item.id < 0
    val hasLink = item.notes.startsWith("http")

    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222222))
        ) {
            FashionImage(
                model = item.imagePath,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            item.name.ifEmpty { item.category.label },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (isEssential) {
            // Essential → search on eBay
            val query = java.net.URLEncoder.encode(item.name, "UTF-8")
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ebay.com.au/sch/i.html?_nkw=$query"))
                    )
                },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Shop", style = MaterialTheme.typography.labelSmall)
            }
        } else if (hasLink) {
            // Secondhand item with original listing URL
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.notes)))
                },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("View", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AmazonKeysDialog(accessKey: String, secretKey: String, associateTag: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var ak by remember { mutableStateOf(accessKey) }
    var sk by remember { mutableStateOf(secretKey) }
    var tag by remember { mutableStateOf(associateTag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AMAZON PA-API", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Register at affiliate-program.amazon.com.au for credentials.", style = MaterialTheme.typography.bodySmall, color = Ash)
                OutlinedTextField(value = ak, onValueChange = { ak = it }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = sk, onValueChange = { sk = it }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Associate Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(ak, sk, tag) }, enabled = ak.isNotBlank() && sk.isNotBlank() && tag.isNotBlank(),
                shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) } }
    )
}
