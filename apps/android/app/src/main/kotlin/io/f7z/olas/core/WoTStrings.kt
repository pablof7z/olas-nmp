package io.f7z.olas.core

/**
 * Canonical WoT disclosure strings, shared across all screens.
 *
 * Keep these in sync with the iOS equivalents in WoTSettingsView.swift and
 * NotificationsView.swift. The authoritative copy lives here; update both
 * platforms together whenever the wording changes.
 *
 * iOS mirror strings:
 *   - WoT settings note: "Network uses your local trust graph. Close is stricter; Open still hides accounts you mute."
 *   - Notifications bar: "Filtered by your trust settings"
 *   - Feed mode label:   "Your extended network"
 */

/** Subtitle shown below the WoT preset cards in WoTSettingsScreen. */
const val WOT_SETTINGS_NOTE =
    "Network uses your local trust graph. Close is stricter; Open still hides accounts you mute."

/** One-line disclosure shown in the notification list header. */
const val WOT_NOTIFICATIONS_NOTE =
    "Filtered by your trust settings"

/** Feed mode label for the network tab — matches iOS "Your extended network". */
const val WOT_NETWORK_FEED_LABEL = "Your extended network"
