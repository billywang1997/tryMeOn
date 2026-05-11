package com.example.myapplication.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.data.repository.OutfitLogRepository
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitLog
import com.example.myapplication.ui.screens.analysis.AnalysisScreen
import com.example.myapplication.ui.screens.calendar.CalendarScreen
import com.example.myapplication.ui.screens.capsule.CapsuleScreen
import com.example.myapplication.ui.screens.cost.CostScreen
import com.example.myapplication.ui.screens.dna.StyleDnaScreen
import com.example.myapplication.ui.screens.emergency.EmergencyScreen
import com.example.myapplication.ui.screens.outfit.OutfitScreen
import com.example.myapplication.ui.screens.profile.ProfileScreen
import com.example.myapplication.ui.screens.rating.RatingScreen
import com.example.myapplication.ui.screens.onboarding.OnboardingScreen
import com.example.myapplication.ui.screens.styleexplorer.StyleExplorerScreen
import com.example.myapplication.ui.screens.shop.ShopScreen
import com.example.myapplication.ui.screens.swipe.SwipeScreen
import com.example.myapplication.ui.screens.tryon.TryOnScreen
import com.example.myapplication.ui.screens.wardrobe.WardrobeScreen
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.White

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Wardrobe : Screen("wardrobe", "CLOSET",  Icons.Default.Checkroom)
    object Outfit   : Screen("outfit",   "OOTD",    Icons.Default.AutoAwesome)
    object TryOn    : Screen("tryon",    "TRY ON",  Icons.Default.Style)
    object Shop     : Screen("shop",     "SHOP",    Icons.Default.ShoppingBag)
    object Profile  : Screen("profile",  "ME",      Icons.Default.Person)
}

val bottomNavItems = listOf(Screen.Wardrobe, Screen.Outfit, Screen.TryOn, Screen.Shop, Screen.Profile)

@Composable
fun AppNavigation(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    replicateApiKey: String,
    ebayClientId: String,
    ebayClientSecret: String,
    rapidApiKey: String = "",
    authRepository: FirebaseAuthRepository? = null,
    firestoreRepository: FirestoreRepository? = null,
    dataStoreManager: DataStoreManager? = null,
    onboardingComplete: Boolean = true,
    onOnboardingComplete: (Set<String>) -> Unit = {},
    styleKeywords: Set<String> = emptySet(),
    amazonAccessKey: String = "",
    amazonSecretKey: String = "",
    amazonAssociateTag: String = "",
    ebayAffiliateCampaignId: String = ""
) {
    val navController: NavHostController = rememberNavController()
    val logRepository = dataStoreManager?.let { OutfitLogRepository(it, firestoreRepository) }

    Scaffold(
        containerColor = White,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val showBottomBar = currentRoute in bottomNavItems.map { it.route } && currentRoute != "onboarding"
            if (showBottomBar) {
                NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, screen.label, tint = if (currentRoute == screen.route) Ink else Ash) },
                            label = { Text(screen.label, style = MaterialTheme.typography.labelSmall, color = if (currentRoute == screen.route) Ink else Ash) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFFEEEEEE),
                                selectedIconColor = Ink, unselectedIconColor = Ash,
                                selectedTextColor = Ink, unselectedTextColor = Ash
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingComplete) Screen.Wardrobe.route else "onboarding"
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    wardrobeRepository = wardrobeRepository,
                    profileRepository = profileRepository,
                    claudeApiService = claudeApiService,
                    apiKey = apiKey,
                    authRepository = authRepository,
                    onComplete = { styles ->
                        onOnboardingComplete(styles)
                        navController.navigate(Screen.Wardrobe.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Wardrobe.route) {
                WardrobeScreen(
                    repository = wardrobeRepository,
                    contentPadding = innerPadding,
                    claudeApiService = claudeApiService,
                    apiKey = apiKey,
                    onNavigateToAnalysis  = { navController.navigate("analysis") },
                    onNavigateToSwipe     = { navController.navigate("swipe") },
                    onNavigateToCapsule   = { navController.navigate("capsule") },
                    onNavigateToDna       = { navController.navigate("dna") },
                    onNavigateToCost      = { navController.navigate("cost") },
                    onNavigateToStyleExplorer = { navController.navigate("styleexplorer") }
                )
            }
            composable(Screen.Outfit.route) {
                OutfitScreen(
                    wardrobeRepository  = wardrobeRepository,
                    profileRepository   = profileRepository,
                    claudeApiService    = claudeApiService,
                    apiKey              = apiKey,
                    contentPadding      = innerPadding,
                    ebayClientId        = ebayClientId,
                    ebayClientSecret    = ebayClientSecret,
                    rapidApiKey         = rapidApiKey,
                    onNavigateToCalendar  = { navController.navigate("calendar") },
                    onNavigateToEmergency = { navController.navigate("emergency") },
                    onNavigateToRating    = { navController.navigate("rating") },
                    onNavigateToTryOn     = { navController.navigate(Screen.TryOn.route) { launchSingleTop = true; restoreState = true } },
                    onNavigateToProfile   = { navController.navigate(Screen.Profile.route) { launchSingleTop = true; restoreState = true } }
                )
            }
            composable(Screen.TryOn.route) {
                TryOnScreen(
                    wardrobeRepository       = wardrobeRepository,
                    profileRepository        = profileRepository,
                    claudeApiService         = claudeApiService,
                    apiKey                   = apiKey,
                    replicateApiKey          = replicateApiKey,
                    contentPadding           = innerPadding,
                    ebayClientId             = ebayClientId,
                    ebayClientSecret         = ebayClientSecret,
                    rapidApiKey              = rapidApiKey,
                    ebayAffiliateCampaignId  = ebayAffiliateCampaignId,
                    amazonAssociateTag       = amazonAssociateTag,
                    styleKeywords            = styleKeywords
                )
            }
            composable(Screen.Shop.route) {
                ShopScreen(
                    ebayClientId        = ebayClientId,
                    ebayClientSecret    = ebayClientSecret,
                    contentPadding      = innerPadding,
                    wardrobeRepository  = wardrobeRepository,
                    claudeApiService    = claudeApiService,
                    replicateApiService = remember { com.example.myapplication.data.remote.ReplicateApiService() },
                    apiKey              = apiKey,
                    replicateApiKey     = replicateApiKey,
                    profileRepository   = profileRepository,
                    amazonAccessKey          = amazonAccessKey,
                    amazonSecretKey          = amazonSecretKey,
                    amazonAssociateTag       = amazonAssociateTag,
                    rapidApiKey              = rapidApiKey,
                    ebayAffiliateCampaignId  = ebayAffiliateCampaignId
                )
            }
            composable(Screen.Profile.route) {
                val auth = authRepository
                val fs = firestoreRepository
                if (auth != null && fs != null) {
                    ProfileScreen(
                        repository = profileRepository,
                        wardrobeRepository = wardrobeRepository,
                        authRepository = auth,
                        firestoreRepository = fs,
                        contentPadding = innerPadding
                    )
                }
            }

            // Feature routes
            composable("analysis") {
                val items by wardrobeRepository.getAllClothing().collectAsState(emptyList())
                AnalysisScreen(userItems = items, onBack = { navController.popBackStack() })
            }
            composable("calendar") {
                if (logRepository != null) {
                    CalendarScreen(
                        logRepository      = logRepository,
                        wardrobeRepository = wardrobeRepository,
                        onBack             = { navController.popBackStack() }
                    )
                }
            }
            composable("swipe") {
                SwipeScreen(wardrobeRepository = wardrobeRepository, onBack = { navController.popBackStack() })
            }
            composable("capsule") {
                CapsuleScreen(
                    wardrobeRepository = wardrobeRepository,
                    claudeApiService   = claudeApiService,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable("dna") {
                val items by wardrobeRepository.getAllClothing().collectAsState(emptyList())
                StyleDnaScreen(userItems = items, onBack = { navController.popBackStack() })
            }
            composable("cost") {
                val items by wardrobeRepository.getAllClothing().collectAsState(emptyList<ClothingItem>())
                val logs by (logRepository?.getLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList<OutfitLog>())).collectAsState(emptyList())
                CostScreen(userItems = items, logs = logs, onBack = { navController.popBackStack() })
            }
            composable("emergency") {
                EmergencyScreen(
                    wardrobeRepository = wardrobeRepository,
                    claudeApiService   = claudeApiService,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable("rating") {
                RatingScreen(
                    claudeApiService   = claudeApiService,
                    profileRepository  = profileRepository,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable("styleexplorer") {
                StyleExplorerScreen(
                    wardrobeRepository = wardrobeRepository,
                    claudeApiService   = claudeApiService,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
        }
    }
}
