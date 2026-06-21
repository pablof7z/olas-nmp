package io.f7z.olas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import io.f7z.olas.core.InviteStore
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.OlasApp
import io.f7z.olas.ui.theme.OlasTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // P2-A: capture invite deep link from the launch intent.
        handleInviteIntent(intent)

        // Initialize the NMP bridge (idempotent — safe to call on every create).
        NMPBridge.initialize(applicationContext)

        setContent {
            OlasTheme {
                OlasApp()
            }
        }
    }

    // P2-A: called by Android when the activity is already running and a new
    // intent arrives (launchMode="singleTask" in the manifest).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInviteIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        NMPBridge.lifecycleForeground()
    }

    override fun onPause() {
        super.onPause()
        NMPBridge.lifecycleBackground()
    }

    // Extract an invite token from any incoming VIEW intent and stash it in
    // InviteStore so OnboardingViewModel can consume it.
    private fun handleInviteIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val str = uri.toString()
        val isInvite = str.startsWith("olas://i/") || str.startsWith("https://olas.app/i/")
        if (isInvite) {
            InviteStore.pendingToken = str
        }
    }
}
