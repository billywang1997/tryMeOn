package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.FirebaseStorageRepository
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.data.remote.AmazonImageSearchService
import com.example.myapplication.data.remote.GoogleImageSearchService
import com.example.myapplication.data.remote.UnsplashService
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.data.sync.CloudSyncManager
import com.example.myapplication.ui.navigation.AppNavigation
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseApp.initializeApp(this)

        val store = DataStoreManager(this)
        val firestoreRepo = FirestoreRepository()
        val storageRepo = FirebaseStorageRepository()
        val wardrobeRepo = WardrobeRepository(store, firestoreRepo, storageRepo)
        val profileRepo = UserProfileRepository(store)
        val claudeService = ClaudeApiService(this)
        val authRepo = FirebaseAuthRepository(this)
        val syncManager = CloudSyncManager(store, firestoreRepo)
        val settings = AppSettings(this)
        UnsplashService.init(settings.unsplashAccessKey)
        GoogleImageSearchService.init(settings.googleSearchApiKey, settings.googleSearchEngineId)
        AmazonImageSearchService.init(settings.amazonAccessKey, settings.amazonSecretKey, settings.amazonAssociateTag)

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
                    claudeApiService = claudeService,
                    apiKey = settings.claudeApiKey,
                    replicateApiKey = settings.fashnApiKey,
                    ebayClientId = settings.ebayClientId,
                    ebayClientSecret = settings.ebayClientSecret,
                    rapidApiKey = settings.rapidApiKey,
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
                    ebayAffiliateCampaignId = settings.ebayAffiliateCampaignId
                )
            }
        }
    }
}
