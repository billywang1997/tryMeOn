package com.trymeon.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trymeon.app.data.auth.FirebaseAuthRepository
import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.FirebaseStorageRepository
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.data.remote.AmazonImageSearchService
import com.trymeon.app.data.remote.GoogleImageSearchService
import com.trymeon.app.data.remote.UnsplashService
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.data.sync.CloudSyncManager
import com.trymeon.app.ui.navigation.AppNavigation
import com.trymeon.app.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Route a notification tap should navigate to (e.g. "wishlist", "streak").
    private val deepLinkRoute = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseApp.initializeApp(this)

        deepLinkRoute.value = intent?.getStringExtra("deep_link_route")
        requestNotificationPermissionIfNeeded()

        val store = DataStoreManager(this)
        val firestoreRepo = FirestoreRepository()
        val storageRepo = FirebaseStorageRepository()
        val wardrobeRepo = WardrobeRepository(store, firestoreRepo, storageRepo)
        val profileRepo = UserProfileRepository(store, firestoreRepo, storageRepo)
        val wishlistRepo = com.trymeon.app.data.repository.WishlistRepository(store, firestoreRepo)
        val marketRepo = com.trymeon.app.data.repository.MarketRepository()
        com.trymeon.app.notifications.NotificationHelper.ensureChannels(this)
        com.trymeon.app.notifications.NotificationScheduler.apply(this)
        val claudeService = ClaudeApiService(this)
        val authRepo = FirebaseAuthRepository(this)
        val settings = AppSettings(this)
        val syncManager = CloudSyncManager(store, firestoreRepo, settings)
        UnsplashService.init(settings.unsplashAccessKey)
        GoogleImageSearchService.init(settings.googleSearchApiKey, settings.googleSearchEngineId)
        AmazonImageSearchService.init(settings.amazonAccessKey, settings.amazonSecretKey, settings.amazonAssociateTag)
        com.trymeon.app.util.Affiliate.init(settings.skimlinksId, settings.sovrnSiteId)
        com.trymeon.app.util.Daigou.init(settings.daigouProviders, settings.preferredDaigouId)

        lifecycleScope.launch {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                authRepo.signInAnonymously()
            } else {
                // Restore from cloud if local cache is empty (new device / reinstall)
                syncManager.restoreIfEmpty(currentUser.uid)
            }
        }

        setContent {
            MyApplicationTheme {
                AppNavigation(
                    wardrobeRepository = wardrobeRepo,
                    profileRepository = profileRepo,
                    wishlistRepository = wishlistRepo,
                    marketRepository = marketRepo,
                    claudeApiService = claudeService,
                    apiKey = settings.claudeApiKey,
                    replicateApiKey = settings.fashnApiKey,
                    ebayClientId = settings.ebayClientId,
                    ebayClientSecret = settings.ebayClientSecret,
                    rapidApiKey = settings.rapidApiKey,
                    serpApiKey = settings.serpApiKey,
                    scraperApiKey = settings.scraperApiKey,
                    authRepository = authRepo,
                    firestoreRepository = firestoreRepo,
                    dataStoreManager = store,
                    onboardingComplete = settings.onboardingComplete,
                    onOnboardingComplete = { styles ->
                        settings.onboardingComplete = true
                        settings.styleKeywords = styles
                    },
                    styleKeywords = settings.styleKeywords,
                    amazonAccessKey = settings.amazonAccessKey,
                    amazonSecretKey = settings.amazonSecretKey,
                    amazonAssociateTag = settings.amazonAssociateTag,
                    ebayAffiliateCampaignId = settings.ebayAffiliateCampaignId,
                    deepLinkRoute = deepLinkRoute.value,
                    onDeepLinkConsumed = { deepLinkRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("deep_link_route")?.let { deepLinkRoute.value = it }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
