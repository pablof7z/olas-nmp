package io.f7z.olas.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.f7z.olas.feature.compose.ComposeScreen
import io.f7z.olas.feature.feed.FeedScreen
import io.f7z.olas.feature.notifications.NotificationsScreen
import io.f7z.olas.feature.onboarding.CreateAccountScreen
import io.f7z.olas.feature.onboarding.FollowPacksScreen
import io.f7z.olas.feature.onboarding.MediaServerScreen
import io.f7z.olas.feature.onboarding.OnboardingCompleteScreen
import io.f7z.olas.feature.onboarding.SignInScreen
import io.f7z.olas.feature.onboarding.WelcomeScreen
import io.f7z.olas.feature.profile.ProfileScreen
import io.f7z.olas.feature.search.SearchScreen
import io.f7z.olas.feature.settings.AccountSecurityScreen
import io.f7z.olas.feature.settings.RelaySettingsScreen
import io.f7z.olas.feature.settings.ServerSettingsScreen
import io.f7z.olas.feature.settings.SettingsScreen
import io.f7z.olas.feature.settings.WalletSettingsScreen
import io.f7z.olas.feature.settings.WoTSettingsScreen
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.components.OlasBottomBar

private fun isOnboardingComplete(context: Context): Boolean {
    val prefs = context.getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_complete", false)
}

@Composable
fun OlasApp() {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val startDest = remember {
        if (isOnboardingComplete(context)) Routes.HOME else Routes.ONBOARDING_WELCOME
    }
    val backEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Routes.HOME, Routes.SEARCH, Routes.NOTIFICATIONS,
        Routes.PROFILE_OWN, Routes.SETTINGS,
    ) || currentRoute?.startsWith("profile/") == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                OlasBottomBar(
                    currentRoute = currentRoute,
                    onHome         = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                    onSearch       = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onCompose      = { navController.navigate(Routes.COMPOSE) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } },
                    onProfile      = { navController.navigate(Routes.PROFILE_OWN) { launchSingleTop = true } },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDest,
            modifier         = Modifier.padding(innerPadding),
        ) {
            // Onboarding
            composable(Routes.ONBOARDING_WELCOME)  { WelcomeScreen(navController) }
            composable(Routes.ONBOARDING_CREATE)   { CreateAccountScreen(navController) }
            composable(Routes.ONBOARDING_FOLLOWS)  { FollowPacksScreen(navController) }
            composable(Routes.ONBOARDING_SERVER)   { MediaServerScreen(navController) }
            composable(Routes.ONBOARDING_COMPLETE) { OnboardingCompleteScreen(navController) }
            composable(Routes.SIGN_IN)             { SignInScreen(navController) }

            // Main tabs
            composable(Routes.HOME)          { FeedScreen(navController) }
            composable(Routes.SEARCH)        { SearchScreen() }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController) }
            composable(Routes.COMPOSE)       { ComposeScreen(navController) }

            // Profile — own (no args) and others (pubkey arg)
            composable(Routes.PROFILE_OWN) { ProfileScreen(navController, pubkey = null) }
            composable(
                route = Routes.PROFILE,
                arguments = listOf(navArgument("pubkey") { type = NavType.StringType }),
            ) { back ->
                val pubkey = back.arguments?.getString("pubkey")
                ProfileScreen(navController, pubkey = pubkey)
            }

            // Settings
            composable(Routes.SETTINGS)          { SettingsScreen(navController) }
            composable(Routes.WOT_SETTINGS)      { WoTSettingsScreen(navController) }
            composable(Routes.RELAY_SETTINGS)    { RelaySettingsScreen(navController) }
            composable(Routes.SERVER_SETTINGS)   { ServerSettingsScreen(navController) }
            composable(Routes.ACCOUNT_SECURITY)  { AccountSecurityScreen(navController) }
            composable(Routes.WALLET_SETTINGS)   { WalletSettingsScreen(navController) }
        }
    }
}
