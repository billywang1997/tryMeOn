package com.trymeon.app.ui.navigation

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
import com.trymeon.app.data.auth.FirebaseAuthRepository
import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.data.repository.OutfitLogRepository
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.screens.calendar.CalendarScreen
import com.trymeon.app.ui.screens.cost.CostScreen
import com.trymeon.app.ui.screens.emergency.EmergencyScreen
import com.trymeon.app.ui.screens.outfit.OutfitScreen
import com.trymeon.app.ui.screens.profile.ProfileScreen
import com.trymeon.app.ui.screens.rating.RatingScreen
import com.trymeon.app.ui.screens.onboarding.OnboardingScreen
import com.trymeon.app.ui.screens.tryon.TryOnScreen
import com.trymeon.app.ui.screens.wardrobe.WardrobeScreen
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.White

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
    wishlistRepository: com.trymeon.app.data.repository.WishlistRepository? = null,
    marketRepository: com.trymeon.app.data.repository.MarketRepository? = null,
    /** Null without a cloud; the "people your size" strips simply do not appear. */
    fitLookRepository: com.trymeon.app.data.repository.FitLookRepository? = null,
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
                    rapidApiKey         = rapidApiKey,
                    scraperApiKey       = scraperApiKey,
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
                    rapidApiKey              = rapidApiKey,
                    scraperApiKey            = scraperApiKey,
                    amazonAssociateTag       = amazonAssociateTag,
                    styleKeywords            = styleKeywords,
                    fitLooks                 = fitLookRepository,
                    authRepository           = authRepository
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
                    com.trymeon.app.data.sourcing.SourcingRepository(
                        sources = listOf(
                            com.trymeon.app.data.remote.AliExpressApiService(),
                            com.trymeon.app.data.remote.TaobaoUnionApiService(),
                            com.trymeon.app.data.remote.ScraperProductSearch(
                                com.trymeon.app.data.remote.TaobaoApiService(),
                                rapidApiKey
                            )
                        ),
                        queryBuilder = com.trymeon.app.data.sourcing.ClaudeSourcingQueryBuilder(
                            service = claudeApiService,
                            apiKey = apiKey,
                            cache = com.trymeon.app.data.sourcing.SourcingReplyCache(),
                            store = com.trymeon.app.data.sourcing.PrefsSourcingReplyStore(sourceContext)
                        ),
                        fxRates = com.trymeon.app.data.sourcing.FxRateRepository(
                            com.trymeon.app.AppSettings(sourceContext)
                        )
                    )
                }
                val sourceWardrobe by wardrobeRepository.getAllClothing().collectAsState(emptyList())
                com.trymeon.app.ui.screens.sourcing.SourceItScreen(
                    repository = sourcingRepository,
                    // Only offered when there is a key to read the photo with;
                    // a camera button that cannot answer is worse than none.
                    identifyPhoto = if (apiKey.isBlank()) null else { uri ->
                        com.trymeon.app.util.PhotoQuery.read(sourceContext, uri, claudeApiService, apiKey)
                    },
                    closetGaps = remember(apiKey) {
                        com.trymeon.app.data.sourcing.ClosetGapService(
                            claudeApiService, apiKey,
                            priceHint = { com.trymeon.app.AppSettings(sourceContext).priceExpectation.stylistHint }
                        )
                    },
                    wardrobe = sourceWardrobe,
                    localPrices = remember(sourceContext) {
                        com.trymeon.app.data.sourcing.AuMarketPrices(
                            com.trymeon.app.data.remote.SerpApiService(),
                            com.trymeon.app.AppSettings(sourceContext).serpApiKey
                        )
                    },
                    gender = profile?.gender.orEmpty(),
                    profile = profile,
                    fitLooks = fitLookRepository,
                    initialQuery = prefilled,
                    onTryOn = {
                        navController.navigate(Screen.TryOn.route) { launchSingleTop = true }
                    },
                    contentPadding = innerPadding
                )
            }
            composable(Screen.Profile.route) {
                // Rendered whether or not there is a cloud. Body, style, price
                // expectation and saved looks are local; gating the whole tab
                // on an account left it blank.
                ProfileScreen(
                        repository = profileRepository,
                        wardrobeRepository = wardrobeRepository,
                        authRepository = authRepository,
                        firestoreRepository = firestoreRepository,
                        contentPadding = innerPadding,
                        claudeApiService = claudeApiService,
                        apiKey = apiKey,
                        styleKeywords = styleKeywords,
                        wishlistRepository = wishlistRepository,
                        logRepository = logRepository,
                        fitLooks = fitLookRepository,
                        onOpenFeature = { navController.navigate(it) }
                    )
            }

            // Feature routes
            composable(FeatureRoutes.CALENDAR) {
                if (logRepository != null) {
                    CalendarScreen(
                        logRepository      = logRepository,
                        wardrobeRepository = wardrobeRepository,
                        onBack             = { navController.popBackStack() }
                    )
                }
            }
            composable(FeatureRoutes.COST) {
                val items by wardrobeRepository.getAllClothing().collectAsState(emptyList<ClothingItem>())
                val logs by (logRepository?.getLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList<OutfitLog>())).collectAsState(emptyList())
                CostScreen(userItems = items, logs = logs, onBack = { navController.popBackStack() })
            }
            composable(FeatureRoutes.EMERGENCY) {
                EmergencyScreen(
                    wardrobeRepository = wardrobeRepository,
                    claudeApiService   = claudeApiService,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable(FeatureRoutes.RATING) {
                RatingScreen(
                    claudeApiService   = claudeApiService,
                    profileRepository  = profileRepository,
                    apiKey             = apiKey,
                    onBack             = { navController.popBackStack() }
                )
            }
            composable(FeatureRoutes.AUDIT) {
                val auditContext = LocalContext.current
                com.trymeon.app.ui.screens.audit.ClosetAuditScreen(
                    wardrobeRepository = wardrobeRepository,
                    profileRepository  = profileRepository,
                    claudeService      = claudeApiService,
                    apiKey             = apiKey,
                    styleKeywords      = styleKeywords,
                    wishlistRepository = wishlistRepository,
                    catalog            = com.trymeon.app.data.sourcing.ShoppingCatalogFactory.create(
                        auditContext, claudeApiService, apiKey, rapidApiKey
                    ),
                    onBack             = { navController.popBackStack() }
                )
            }
            composable(FeatureRoutes.WISHLIST) {
                if (wishlistRepository != null) {
                    val wishlistContext = LocalContext.current
                    com.trymeon.app.ui.screens.wishlist.WishlistScreen(
                        repository = wishlistRepository,
                        catalog = com.trymeon.app.data.sourcing.ShoppingCatalogFactory.create(
                            wishlistContext, claudeApiService, apiKey, rapidApiKey
                        ),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(FeatureRoutes.STREAK) {
                if (logRepository != null) {
                    com.trymeon.app.ui.screens.streak.StreakScreen(
                        wardrobeRepository = wardrobeRepository,
                        logRepository = logRepository,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
