package com.example.myapplication.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.data.auth.FirebaseAuthRepository
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.data.repository.OutfitLogRepository
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitLog
import com.example.myapplication.ui.screens.calendar.CalendarScreen
import com.example.myapplication.ui.screens.cost.CostScreen
import com.example.myapplication.ui.screens.emergency.EmergencyScreen
import com.example.myapplication.ui.screens.outfit.OutfitScreen
import com.example.myapplication.ui.screens.profile.ProfileScreen
import com.example.myapplication.ui.screens.rating.RatingScreen
import com.example.myapplication.ui.screens.onboarding.OnboardingScreen
import com.example.myapplication.ui.screens.tryon.TryOnScreen
import com.example.myapplication.ui.screens.wardrobe.WardrobeScreen
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.White

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Wardrobe : Screen("wardrobe", "CLOSET",  Icons.Default.Checkroom)
    object Outfit   : Screen("outfit",   "OOTD",    Icons.Default.AutoAwesome)
    object TryOn    : Screen("tryon",    "TRY ON",  Icons.Default.Style)
    object Source   : Screen("source",   "SOURCE",  Icons.Default.ShoppingBag)
    object Profile  : Screen("profile",  "ME",      Icons.Default.Person)
}

val bottomNavItems = listOf(Screen.Wardrobe, Screen.Outfit, Screen.TryOn, Screen.Source, Screen.Profile)

@Composable
fun AppNavigation(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository,
    wishlistRepository: com.example.myapplication.data.repository.WishlistRepository? = null,
    marketRepository: com.example.myapplication.data.repository.MarketRepository? = null,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    replicateApiKey: String,
    ebayClientId: String,
    ebayClientSecret: String,
    rapidApiKey: String = "",
    serpApiKey: String = "",
    scraperApiKey: String = "",
    authRepository: FirebaseAuthRepository? = null,
    firestoreRepository: FirestoreRepository? = null,
    dataStoreManager: DataStoreManager? = null,
    onboardingComplete: Boolean = true,
    onOnboardingComplete: (Set<String>) -> Unit = {},
    styleKeywords: Set<String> = emptySet(),
    amazonAccessKey: String = "",
    amazonSecretKey: String = "",
    amazonAssociateTag: String = "",
    ebayAffiliateCampaignId: String = "",
    deepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController: NavHostController = rememberNavController()

    // Navigate when a notification deep-link arrives.
    LaunchedEffect(deepLinkRoute) {
        val route = deepLinkRoute ?: return@LaunchedEffect
        runCatching { navController.navigate(route) }
        onDeepLinkConsumed()
    }
    val logRepository = dataStoreManager?.let { OutfitLogRepository(it, firestoreRepository) }

    Scaffold(
        containerColor = White,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            // Compare the base path: a tab route may carry arguments (source?q=…),
            // and an exact match would hide the bar on that tab entirely.
            val baseRoute = currentRoute?.substringBefore('?')
            val showBottomBar = baseRoute in bottomNavItems.map { it.route } && baseRoute != "onboarding"
            if (showBottomBar) {
                NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, screen.label, tint = if (currentRoute?.substringBefore('?') == screen.route) Ink else Ash) },
                            label = { Text(screen.label, style = MaterialTheme.typography.labelSmall, color = if (currentRoute?.substringBefore('?') == screen.route) Ink else Ash) },
                            // Routes may carry arguments (source?q=…); the tab is
                            // still the same destination, so compare the base path.
                            selected = currentRoute?.substringBefore('?') == screen.route,
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
                    firestoreRepository = firestoreRepository,
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
                    logRepository = logRepository,
                    profileRepository = profileRepository,
                    wishlistRepository = wishlistRepository,
                    marketRepository = marketRepository,
                    ebayClientId = ebayClientId,
                    ebayClientSecret = ebayClientSecret,
                    serpApiKey = serpApiKey,
                    ebayAffiliateCampaignId = ebayAffiliateCampaignId,
                    onNavigateToCost      = { navController.navigate("cost") },
                    onNavigateToAudit     = { navController.navigate("audit") },
                    onNavigateToWishlist  = { navController.navigate("wishlist") },
                    onNavigateToStreak    = { navController.navigate("streak") }
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
                    scraperApiKey       = scraperApiKey,
                    serpApiKey          = serpApiKey,
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
                    scraperApiKey            = scraperApiKey,
                    serpApiKey               = serpApiKey,
                    ebayAffiliateCampaignId  = ebayAffiliateCampaignId,
                    amazonAssociateTag       = amazonAssociateTag,
                    styleKeywords            = styleKeywords
                )
            }
            composable(
                route = "${Screen.Source.route}?q={q}",
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val prefilled = entry.arguments?.getString("q").orEmpty()
                val profile by profileRepository.getProfile().collectAsState(null)
                val sourceContext = LocalContext.current
                val sourcingRepository = remember(apiKey, rapidApiKey) {
                    com.example.myapplication.data.sourcing.SourcingRepository(
                        sources = listOf(
                            com.example.myapplication.data.remote.TaobaoUnionApiService(),
                            com.example.myapplication.data.remote.ScraperProductSearch(
                                com.example.myapplication.data.remote.TaobaoApiService(),
                                rapidApiKey
                            )
                        ),
                        queryBuilder = com.example.myapplication.data.sourcing.ClaudeSourcingQueryBuilder(
                            service = claudeApiService,
                            apiKey = apiKey
                        ),
                        fxRates = com.example.myapplication.data.sourcing.FxRateRepository(
                            com.example.myapplication.AppSettings(sourceContext)
                        )
                    )
                }
                val sourceWardrobe by wardrobeRepository.getAllClothing().collectAsState(emptyList())
                com.example.myapplication.ui.screens.sourcing.SourceItScreen(
                    repository = sourcingRepository,
                    closetGaps = remember(apiKey) {
                        com.example.myapplication.data.sourcing.ClosetGapService(claudeApiService, apiKey)
                    },
                    wardrobe = sourceWardrobe,
                    gender = profile?.gender.orEmpty(),
                    initialQuery = prefilled,
                    onTryOn = {
                        navController.navigate(Screen.TryOn.route) { launchSingleTop = true }
                    },
                    contentPadding = innerPadding
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
                        contentPadding = innerPadding,
                        claudeApiService = claudeApiService,
                        apiKey = apiKey,
                        ebayClientId = ebayClientId,
                        ebayClientSecret = ebayClientSecret,
                        serpApiKey = serpApiKey,
                        ebayAffiliateCampaignId = ebayAffiliateCampaignId,
                        styleKeywords = styleKeywords,
                        wishlistRepository = wishlistRepository,
                        logRepository = logRepository
                    )
                }
            }

            // Feature routes
            composable("calendar") {
                if (logRepository != null) {
                    CalendarScreen(
                        logRepository      = logRepository,
                        wardrobeRepository = wardrobeRepository,
                        onBack             = { navController.popBackStack() }
                    )
                }
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
            composable("audit") {
                com.example.myapplication.ui.screens.audit.ClosetAuditScreen(
                    wardrobeRepository = wardrobeRepository,
                    profileRepository  = profileRepository,
                    claudeService      = claudeApiService,
                    apiKey             = apiKey,
                    ebayClientId       = ebayClientId,
                    ebayClientSecret   = ebayClientSecret,
                    serpApiKey         = serpApiKey,
                    ebayAffiliateCampaignId = ebayAffiliateCampaignId,
                    styleKeywords      = styleKeywords,
                    wishlistRepository = wishlistRepository,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable("wishlist") {
                if (wishlistRepository != null) {
                    com.example.myapplication.ui.screens.wishlist.WishlistScreen(
                        repository = wishlistRepository,
                        serpApiKey = serpApiKey,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("streak") {
                if (logRepository != null) {
                    com.example.myapplication.ui.screens.streak.StreakScreen(
                        wardrobeRepository = wardrobeRepository,
                        logRepository = logRepository,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
