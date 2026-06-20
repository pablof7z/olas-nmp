package io.f7z.olas.core

/**
 * Canonical WoT-gap disclosure strings, shared across all screens.
 *
 * Keep these in sync with the iOS equivalents in WoTSettingsView.swift and
 * NotificationsView.swift. The authoritative copy lives here; update both
 * platforms together whenever the wording changes.
 *
 * iOS mirror strings:
 *   - WoT settings note: "Trust scoring is updating in the background. Filtering will apply once complete."
 *   - Notifications bar: WoT filter note (inline comment, no visible label)
 *   - Feed mode label:   "Your extended network"
 */

/** Subtitle shown below the WoT preset cards in WoTSettingsScreen. */
const val WOT_GAP_SETTINGS_NOTE =
    "Trust scoring is updating in the background. Filtering will apply once complete."

/** One-line disclosure shown in the notification list header while WoT gap is active. */
const val WOT_GAP_NOTIFICATIONS_NOTE =
    "Showing all · Trust scoring updating in the background"

/** Feed mode label for the network (unfiltered) tab — matches iOS "Your extended network". */
const val WOT_GAP_NETWORK_FEED_LABEL = "Your extended network"

const val WOT_SETTINGS_NOTE =
    "Network filtering uses the active trust graph. Close is stricter; Open still honors mutes and blocks."

const val WOT_NOTIFICATIONS_NOTE =
    "Notifications are filtered through the active trust graph."

const val WOT_NETWORK_FEED_LABEL = "Your extended network"
