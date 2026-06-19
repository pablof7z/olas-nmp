package io.f7z.olas.navigation

/** Canonical route strings for the NavHost. */
object Routes {
    const val HOME          = "home"
    const val SEARCH        = "search"
    const val NOTIFICATIONS = "notifications"
    const val COMPOSE       = "compose"
    const val PROFILE_OWN   = "profile"
    const val PROFILE       = "profile/{pubkey}"
    const val SETTINGS      = "settings"
    const val WOT_SETTINGS  = "settings/wot"
    const val RELAY_SETTINGS    = "settings/relays"
    const val SERVER_SETTINGS   = "settings/servers"
    const val ACCOUNT_SECURITY  = "settings/security"
    const val WALLET_SETTINGS   = "settings/wallet"

    // Onboarding sub-graph
    const val ONBOARDING_WELCOME  = "onboarding/welcome"
    const val ONBOARDING_CREATE   = "onboarding/create"
    const val ONBOARDING_FOLLOWS  = "onboarding/follows"
    const val ONBOARDING_SERVER   = "onboarding/server"
    const val ONBOARDING_COMPLETE = "onboarding/complete"
    const val SIGN_IN             = "onboarding/signin"

    // Dynamic routes
    fun profile(pubkey: String) = "profile/$pubkey"
}
