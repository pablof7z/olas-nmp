package io.f7z.olas.core

/**
 * In-process store for a pending invite token.
 *
 * MainActivity writes [pendingToken] when the app is opened via an invite deep
 * link (olas://i/<npub> or https://olas.app/i/<npub>). OnboardingViewModel
 * reads and clears it exactly once during onboarding. If onboarding is already
 * complete the token is silently dropped — the invite already served its purpose.
 *
 * The value is volatile so reads from the Compose thread see the write from the
 * main thread without synchronisation overhead.
 */
object InviteStore {
    @Volatile
    var pendingToken: String? = null
}
