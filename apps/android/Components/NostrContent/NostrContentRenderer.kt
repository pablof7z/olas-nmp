// Requires: compose-ui, compose-foundation, compose-material3 (for default
// MaterialTheme colors). Kotlin 1.9+.
//
// Compose mirror of the SwiftUI `NostrContentRenderer` environment value.
// Themes + tap callbacks for the entire installed content-component family
// flow through `LocalNostrContentRenderer`. Apps inject a renderer once at
// the top of their composition tree:
//
//     CompositionLocalProvider(
//         LocalNostrContentRenderer provides NostrContentRenderer(
//             mentionColor = MaterialTheme.colorScheme.primary,
//             callbacks = NostrContentCallbacks(
//                 onMentionTap = { pubkey -> navigateToProfile(pubkey) },
//                 onLinkTap = { url -> openUrl(url) },
//             ),
//         ),
//     ) {
//         NostrContentView(tree = tree)
//     }
//
// Child components read the renderer with
// `LocalNostrContentRenderer.current`.

package nmp.content

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

public data class NostrContentCallbacks(
    val onMentionTap: (String) -> Unit = {},
    val onHashtagTap: (String) -> Unit = {},
    val onLinkTap: (String) -> Unit = {},
    /**
     * Tap-on-image handler. Defaults to [onLinkTap] when not supplied so apps
     * that only wire the generic link handler still get image-tap routing for
     * free (mirrors the SwiftUI initializer).
     */
    val onImageTap: (String) -> Unit = onLinkTap,
    val onEventRefTap: (String) -> Unit = {},
)

public data class NostrContentRenderer(
    val textColor: Color = Color.Unspecified,
    val secondaryTextColor: Color = Color.Unspecified,
    val mentionColor: Color = Color.Unspecified,
    val hashtagColor: Color = Color.Unspecified,
    val linkColor: Color = Color.Unspecified,
    val quoteBorderColor: Color = Color(0x59999999),
    val quoteBackgroundColor: Color = Color(0x14999999),
    val codeBackgroundColor: Color = Color(0x26999999),
    val placeholderColor: Color = Color(0x99999999),
    val callbacks: NostrContentCallbacks = NostrContentCallbacks(),
)

/**
 * CompositionLocal carrying the active [NostrContentRenderer]. Uses
 * [compositionLocalOf] (not `staticCompositionLocalOf`) because the renderer
 * is intentionally swappable per-screen.
 */
public val LocalNostrContentRenderer = compositionLocalOf { NostrContentRenderer() }
