package io.f7z.olas.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View

// ---------------------------------------------------------------------------
// Haptics
// ---------------------------------------------------------------------------

/**
 * Thin wrappers around [View.performHapticFeedback] for Olas interaction semantics.
 *
 * The OS gates execution automatically — Android respects "Touch vibration" in
 * Settings → Sound & Vibration. No extra guarding is needed here.
 *
 * Usage in Compose: obtain [View] via `val view = LocalView.current` and pass it.
 */
object OlasHaptics {

    /** Light click — like/heart tap. */
    fun impactLight(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Rigid click — filter carousel per-cell selection tick. */
    fun impactRigid(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Soft thud — new-posts pill tap, revealing queued content. */
    fun impactSoft(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Selection tick — feed mode tab switch and new-posts pill appear. */
    fun selectionChanged(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Success notification — zap sent or post published.
     * Uses [HapticFeedbackConstants.CONFIRM] on API 30+ for a triple-tap feel;
     * falls back to [HapticFeedbackConstants.VIRTUAL_KEY] on older releases.
     */
    fun notificationSuccess(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

// ---------------------------------------------------------------------------
// Sound
// ---------------------------------------------------------------------------

/**
 * Opt-in sound effects for Olas, gated by a [SharedPreferences] boolean
 * `"sound_effects_enabled"` in the `"olas_prefs"` file (default `false`).
 *
 * Uses [AudioAttributes.USAGE_ASSISTANCE_SONIFICATION] so effects:
 * - respect the hardware ringer/vibration mode,
 * - do not interrupt background music.
 *
 * Audio assets (`shutter_soft.ogg`, `zap_chime.ogg`) live in `res/raw/` and
 * are **optional**. If an asset is absent the call is a silent no-op. Real files
 * can be added later without changing call-site code.
 */
object OlasSound {

    private const val PREFS_FILE = "olas_prefs"
    private const val KEY_ENABLED = "sound_effects_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /** Soft camera shutter — plays on successful post publish. */
    fun shutterSoft(context: Context) {
        if (!isEnabled(context)) return
        playRaw(context, "shutter_soft")
    }

    /** Warm chime — plays when a zap is sent. */
    fun zapChime(context: Context) {
        if (!isEnabled(context)) return
        playRaw(context, "zap_chime")
    }

    // -------------------------------------------------------------------------

    /**
     * Loads and plays a `res/raw/<name>` asset via a short-lived [SoundPool].
     * The pool is released ~3 s after load completes (well beyond any UI sound duration).
     * Returns silently if the resource identifier is 0 (asset absent).
     */
    private fun playRaw(context: Context, name: String) {
        val appContext = context.applicationContext
        val resId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        if (resId == 0) return // Asset not yet bundled — silent no-op by design.

        runCatching {
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()

            pool.setOnLoadCompleteListener { soundPool, sampleId, _ ->
                soundPool.play(sampleId, 1f, 1f, 0, 0, 1f)
                // Release after a generous delay to allow playback to finish.
                Handler(Looper.getMainLooper()).postDelayed({ soundPool.release() }, 3_000L)
            }
            pool.load(appContext, resId, 1)
        }
        // Silently swallow any SoundPool construction/load failure.
    }
}
