package io.f7z.olas.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.f7z.olas.core.OlasProfileHost
import org.nmp.registry.LocalNostrProfileHost
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.f7z.olas.feature.compose.ComposeScreen
import io.f7z.olas.feature.feed.FeedScreen
import io.f7z.olas.feature.feed.FullscreenImageScreen
import io.f7z.olas.feature.notifications.NotificationsScreen
import io.f7z.olas.feature.onboarding.CreateAccountScreen
import io.f7z.olas.feature.onboarding.FollowPacksScreen
import io.f7z.olas.feature.onboarding.OnboardingCompleteScreen
import io.f7z.olas.feature.onboarding.SignInScreen
import io.f7z.olas.feature.onboarding.WelcomeScreen
import io.f7z.olas.feature.profile.ProfileScreen
import io.f7z.olas.feature.search.SearchScreen
import io.f7z.olas.feature.settings.AccountSecurityScreen
import io.f7z.olas.feature.settings.RecoveryKeyScreen
import io.f7z.olas.feature.settings.RelaySettingsScreen
import io.f7z.olas.feature.settings.ServerSettingsScreen
import io.f7z.olas.feature.settings.SettingsScreen
import io.f7z.olas.feature.settings.WalletSettingsScreen
import io.f7z.olas.feature.settings.WoTSettingsScreen
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.components.OlasBottomBar
import io.f7z.olas.ui.theme.OlasColors

private fun isOnboardingComplete(context: Context): Boolean {
    val prefs = context.getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_complete", false)
}

private fun isCoachmarkSeen(context: Context): Boolean {
    val prefs = context.getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("first_post_coachmark_seen", false)
}

/** True only when the account was freshly created (not signed in via nsec/bunker). */
private fun isNewAccount(context: Context): Boolean {
    val prefs = context.getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_new_account", false)
}

internal fun markCoachmarkSeen(context: Context) {
    context.getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("first_post_coachmark_seen", true)
        .apply()
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

    // Reduce Motion: when animator scale is 0 use crossfade-only (no zoom).
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    // Photo Lift overlay: shown above the tab bar for a full-screen zoom effect.
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }

    var coachmarkVisible by remember {
        mutableStateOf(
            isOnboardingComplete(context) && !isCoachmarkSeen(context) && isNewAccount(context)
        )
    }

    val showBottomBar = currentRoute in setOf(
        Routes.HOME, Routes.SEARCH, Routes.NOTIFICATIONS,
        Routes.PROFILE_OWN, Routes.SETTINGS,
    ) || currentRoute?.startsWith("profile/") == true

    // Callback forwarded from feed and profile screens.
    val onImageTap: (String) -> Unit = { url -> fullscreenUrl = url }

    // Intercept system Back while the photo overlay is visible so it closes
    // the overlay instead of falling through to the NavController (which would
    // pop the back-stack or exit the app on the HOME destination).
    BackHandler(enabled = fullscreenUrl != null) { fullscreenUrl = null }

    CompositionLocalProvider(LocalNostrProfileHost provides OlasProfileHost) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (showBottomBar) {
                        OlasBottomBar(
                            currentRoute    = currentRoute,
                            onHome          = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                            onSearch        = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                            onCompose       = {
                                coachmarkVisible = false
                                markCoachmarkSeen(context)
                                navController.navigate(Routes.COMPOSE)
                            },
                            onNotifications = { navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } },
                            onProfile       = { navController.navigate(Routes.PROFILE_OWN) { launchSingleTop = true } },
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
                    composable(Routes.ONBOARDING_FOLLOWS)  {
                        FollowPacksScreen(onContinue = { navController.navigate(Routes.ONBOARDING_COMPLETE) })
                    }
                    composable(Routes.ONBOARDING_COMPLETE) { OnboardingCompleteScreen(navController) }
                    composable(Routes.SIGN_IN)             { SignInScreen(navController) }

                    // Main tabs
                    composable(Routes.HOME)          { FeedScreen(navController, onImageTap = onImageTap) }
                    composable(Routes.SEARCH)        { SearchScreen(navController) }
                    composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController) }
                    composable(Routes.COMPOSE)       { ComposeScreen(navController) }

                    // Profile — own (no args) and others (pubkey arg)
                    composable(Routes.PROFILE_OWN) {
                        ProfileScreen(navController, pubkey = null, onImageTap = onImageTap)
                    }
                    composable(
                        route = Routes.PROFILE,
                        arguments = listOf(navArgument("pubkey") { type = NavType.StringType }),
                    ) { back ->
                        val pubkey = back.arguments?.getString("pubkey")
                        ProfileScreen(navController, pubkey = pubkey, onImageTap = onImageTap)
                    }

                    // Settings
                    composable(Routes.SETTINGS)          { SettingsScreen(navController) }
                    composable(Routes.WOT_SETTINGS)      { WoTSettingsScreen(navController) }
                    composable(Routes.RELAY_SETTINGS)    { RelaySettingsScreen(navController) }
                    composable(Routes.SERVER_SETTINGS)   { ServerSettingsScreen(navController) }
                    composable(Routes.ACCOUNT_SECURITY)  { AccountSecurityScreen(navController) }
                    composable(Routes.RECOVERY_KEY)      { RecoveryKeyScreen(navController) }
                    composable(Routes.WALLET_SETTINGS)   { WalletSettingsScreen(navController) }
                }
            }

            // First-post coachmark — overlays above bottom bar when visible.
            AnimatedVisibility(
                visible = coachmarkVisible && showBottomBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                FirstPostCoachmark(
                    onDismiss = {
                        coachmarkVisible = false
                        markCoachmarkSeen(context)
                    },
                    onTap = {
                        coachmarkVisible = false
                        markCoachmarkSeen(context)
                        navController.navigate(Routes.COMPOSE)
                    },
                )
            }

            // Photo Lift overlay — covers tab bar for full-screen zoom feel.
            // Reduce Motion: crossfade only. Normal: scale-zoom in/out.
            AnimatedVisibility(
                visible  = fullscreenUrl != null,
                modifier = Modifier.fillMaxSize(),
                enter    = if (reduceMotion) fadeIn()
                           else scaleIn(initialScale = 0.88f) + fadeIn(),
                exit     = if (reduceMotion) fadeOut()
                           else scaleOut(targetScale = 0.88f) + fadeOut(),
            ) {
                fullscreenUrl?.let { url ->
                    FullscreenImageScreen(
                        url       = url,
                        onDismiss = { fullscreenUrl = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstPostCoachmark(
    onDismiss: () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OlasColors.Surface2, RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Share your first photo",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = OlasColors.Text1,
            )
            Text(
                "Tap + to post your first photo.",
                fontSize = 13.sp,
                color    = OlasColors.Text2,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint               = OlasColors.Text3,
            )
        }
    }
}
