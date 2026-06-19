package io.f7z.olas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.ui.OlasApp
import io.f7z.olas.ui.theme.OlasTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize the NMP bridge (idempotent — safe to call on every create).
        NMPBridge.initialize(applicationContext)

        setContent {
            OlasTheme {
                OlasApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NMPBridge.lifecycleForeground()
    }

    override fun onPause() {
        super.onPause()
        NMPBridge.lifecycleBackground()
    }
}
