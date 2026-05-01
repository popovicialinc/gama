package com.popovicialinc.gama

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat

/**
 * GAMA's mechanical feedback layer.
 *
 * The app intentionally has no sound effects, so haptics are treated as a real UI
 * language: light contact clicks, deliberate hold blooms, renderer intent clicks,
 * language signatures, and physical layout dodge/return ticks.
 */
object GamaHaptics {

    private const val HOLD_RELEASE_THRESHOLD_MS = 230L
    private const val REGULAR_CLICK_SUPPRESS_WINDOW_MS = 110L

    const val PREF_ENABLED = "haptics_enabled"
    const val PREF_REGULAR_ENABLED = "haptics_regular_enabled"
    const val PREF_HOLD_ENABLED = "haptics_hold_enabled"
    const val PREF_RENDERER_ENABLED = "haptics_renderer_enabled"
    const val PREF_LANGUAGE_ENABLED = "haptics_language_enabled"
    const val PREF_BOUNCE_ENABLED = "haptics_bounce_enabled"

    const val PREF_REGULAR_STRENGTH = "haptics_regular_strength"
    const val PREF_HOLD_STRENGTH = "haptics_hold_strength"
    const val PREF_RENDERER_STRENGTH = "haptics_renderer_strength"
    const val PREF_LANGUAGE_STRENGTH = "haptics_language_strength"
    const val PREF_BOUNCE_STRENGTH = "haptics_bounce_strength"
    const val PREF_BOUNCE_RETURN_STRENGTH = "haptics_bounce_return_strength"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_REGULAR_ENABLED = true
    const val DEFAULT_HOLD_ENABLED = true
    const val DEFAULT_RENDERER_ENABLED = true
    const val DEFAULT_LANGUAGE_ENABLED = true
    const val DEFAULT_BOUNCE_ENABLED = true

    const val DEFAULT_REGULAR_STRENGTH = 58
    const val DEFAULT_HOLD_STRENGTH = 82
    const val DEFAULT_RENDERER_STRENGTH = 68
    const val DEFAULT_LANGUAGE_STRENGTH = 70
    const val DEFAULT_BOUNCE_STRENGTH = 38
    const val DEFAULT_BOUNCE_RETURN_STRENGTH = 66

    private var lastSurfaceHapticAtMs: Long = 0L

    fun feedback(context: Context, view: View, type: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        when (type) {
            HapticFeedbackConstants.CLOCK_TICK -> selection(context, view)
            HapticFeedbackConstants.LONG_PRESS -> hold(context, view)
            HapticFeedbackConstants.CONFIRM -> success(context, view)
            HapticFeedbackConstants.REJECT -> error(context, view)
            else -> {
                if (recentSurfaceHaptic()) return
                lightClick(context, view)
            }
        }
    }

    fun click(view: View, type: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        view.performHapticFeedback(type)
    }

    fun lightClick(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_REGULAR_ENABLED, DEFAULT_REGULAR_ENABLED)) return
        val amp = scaled(context, PREF_REGULAR_STRENGTH, 76, 36, 138, DEFAULT_REGULAR_STRENGTH, strengthOverride)
        if (!vibrateOneShot(context, 16L, amp)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun navigation(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_REGULAR_ENABLED, DEFAULT_REGULAR_ENABLED)) return
        val amp = scaled(context, PREF_REGULAR_STRENGTH, 86, 42, 150, DEFAULT_REGULAR_STRENGTH, strengthOverride)
        if (!vibrateOneShot(context, 17L, amp)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun navigation(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun selection(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_REGULAR_ENABLED, DEFAULT_REGULAR_ENABLED)) return
        val amp = scaled(context, PREF_REGULAR_STRENGTH, 62, 28, 116, DEFAULT_REGULAR_STRENGTH, strengthOverride)
        if (!vibrateOneShot(context, 12L, amp)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun hold(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_HOLD_ENABLED, DEFAULT_HOLD_ENABLED)) return
        val a1 = scaled(context, PREF_HOLD_STRENGTH, 150, 82, 245, DEFAULT_HOLD_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_HOLD_STRENGTH, 92, 42, 176, DEFAULT_HOLD_STRENGTH, strengthOverride)
        if (!vibratePattern(context, longArrayOf(0L, 24L, 26L, 18L), intArrayOf(0, a1, 0, a2))) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Finger touched a regular surface. Returns a timestamp for releaseAfterPress(). */
    fun pressStart(context: Context, view: View): Long {
        val now = SystemClock.uptimeMillis()
        if (!categoryEnabled(context, PREF_REGULAR_ENABLED, DEFAULT_REGULAR_ENABLED)) return now
        lastSurfaceHapticAtMs = now
        val amp = scaled(context, PREF_REGULAR_STRENGTH, 82, 40, 150, DEFAULT_REGULAR_STRENGTH)
        if (!vibrateOneShot(context, 16L, amp)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        return now
    }

    /** Finger touched Vulkan/OpenGL. Firmer than a normal contact, but still clean. */
    fun rendererPressStart(context: Context, view: View): Long {
        val now = SystemClock.uptimeMillis()
        if (!categoryEnabled(context, PREF_RENDERER_ENABLED, DEFAULT_RENDERER_ENABLED)) return now
        lastSurfaceHapticAtMs = now
        val amp = scaled(context, PREF_RENDERER_STRENGTH, 118, 62, 190, DEFAULT_RENDERER_STRENGTH)
        if (!vibrateOneShot(context, 18L, amp)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        return now
    }

    /**
     * Fires only for deliberate holds. Quick taps keep exactly one haptic pulse:
     * the contact pulse from pressStart()/rendererPressStart().
     */
    fun releaseAfterPress(context: Context, view: View, startedAtMs: Long, released: Boolean, strengthOverride: Int? = null) {
        if (!released) return
        if (!categoryEnabled(context, PREF_HOLD_ENABLED, DEFAULT_HOLD_ENABLED)) return
        val heldForMs = SystemClock.uptimeMillis() - startedAtMs
        if (heldForMs < HOLD_RELEASE_THRESHOLD_MS) return

        lastSurfaceHapticAtMs = SystemClock.uptimeMillis()
        val a1 = scaled(context, PREF_HOLD_STRENGTH, 228, 128, 255, DEFAULT_HOLD_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_HOLD_STRENGTH, 174, 88, 240, DEFAULT_HOLD_STRENGTH, strengthOverride)
        val a3 = scaled(context, PREF_HOLD_STRENGTH, 108, 48, 188, DEFAULT_HOLD_STRENGTH, strengthOverride)
        if (!vibratePattern(
                context,
                longArrayOf(0L, 34L, 30L, 26L, 42L, 18L),
                intArrayOf(0, a1, 0, a2, 0, a3)
            )
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Stronger than normal UI, used after Vulkan/OpenGL commits. */
    fun rendererSelection(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_RENDERER_ENABLED, DEFAULT_RENDERER_ENABLED)) return
        val a1 = scaled(context, PREF_RENDERER_STRENGTH, 158, 78, 230, DEFAULT_RENDERER_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_RENDERER_STRENGTH, 96, 42, 176, DEFAULT_RENDERER_STRENGTH, strengthOverride)
        if (!vibratePattern(
                context,
                longArrayOf(0L, 22L, 28L, 16L),
                intArrayOf(0, a1, 0, a2)
            )
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /** Small premium signature for changing language: contact → bloom → tiny tail. */
    fun languageChanged(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_LANGUAGE_ENABLED, DEFAULT_LANGUAGE_ENABLED)) return
        val a1 = scaled(context, PREF_LANGUAGE_STRENGTH, 72, 34, 134, DEFAULT_LANGUAGE_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_LANGUAGE_STRENGTH, 174, 88, 240, DEFAULT_LANGUAGE_STRENGTH, strengthOverride)
        val a3 = scaled(context, PREF_LANGUAGE_STRENGTH, 82, 30, 148, DEFAULT_LANGUAGE_STRENGTH, strengthOverride)
        if (!vibratePattern(
                context,
                longArrayOf(0L, 12L, 22L, 28L, 38L, 12L),
                intArrayOf(0, a1, 0, a2, 0, a3)
            )
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /** Gentle tick when cards dodge left away from the floating back button. */
    fun avoidanceDodge(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_BOUNCE_ENABLED, DEFAULT_BOUNCE_ENABLED)) return
        val a1 = scaled(context, PREF_BOUNCE_STRENGTH, 48, 16, 118, DEFAULT_BOUNCE_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_BOUNCE_STRENGTH, 28, 8, 78, DEFAULT_BOUNCE_STRENGTH, strengthOverride)
        if (!vibratePattern(context, longArrayOf(0L, 8L, 26L, 7L), intArrayOf(0, a1, 0, a2))) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Stronger settling pulse when cards return to normal width. */
    fun avoidanceReturn(context: Context, view: View, strengthOverride: Int? = null) {
        if (!categoryEnabled(context, PREF_BOUNCE_ENABLED, DEFAULT_BOUNCE_ENABLED)) return
        val a1 = scaled(context, PREF_BOUNCE_RETURN_STRENGTH, 112, 48, 210, DEFAULT_BOUNCE_RETURN_STRENGTH, strengthOverride)
        val a2 = scaled(context, PREF_BOUNCE_RETURN_STRENGTH, 66, 22, 138, DEFAULT_BOUNCE_RETURN_STRENGTH, strengthOverride)
        if (!vibratePattern(context, longArrayOf(0L, 16L, 30L, 11L), intArrayOf(0, a1, 0, a2))) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Backwards-compatible alias used by older call-sites/previews. */
    fun avoidanceBounce(context: Context, view: View, strengthOverride: Int? = null) {
        avoidanceDodge(context, view, strengthOverride)
    }

    fun success(context: Context, view: View) {
        val a1 = scaled(context, PREF_REGULAR_STRENGTH, 94, 40, 166, DEFAULT_REGULAR_STRENGTH)
        val a2 = scaled(context, PREF_REGULAR_STRENGTH, 150, 70, 230, DEFAULT_REGULAR_STRENGTH)
        if (!vibratePattern(context, longArrayOf(0L, 18L, 36L, 26L), intArrayOf(0, a1, 0, a2))) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    fun warning(context: Context, view: View) {
        val a1 = scaled(context, PREF_REGULAR_STRENGTH, 136, 60, 220, DEFAULT_REGULAR_STRENGTH)
        val a2 = scaled(context, PREF_REGULAR_STRENGTH, 88, 38, 166, DEFAULT_REGULAR_STRENGTH)
        if (!vibratePattern(context, longArrayOf(0L, 28L, 52L, 20L), intArrayOf(0, a1, 0, a2))) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun error(context: Context, view: View) {
        if (!hapticsEnabled(context)) return
        if (!vibratePattern(context, longArrayOf(0L, 24L, 44L, 24L, 44L, 30L), intArrayOf(0, 178, 0, 154, 0, 204))) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }

    fun resetToDefaults(context: Context) {
        context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_ENABLED, DEFAULT_ENABLED)
            .putBoolean(PREF_REGULAR_ENABLED, DEFAULT_REGULAR_ENABLED)
            .putBoolean(PREF_HOLD_ENABLED, DEFAULT_HOLD_ENABLED)
            .putBoolean(PREF_RENDERER_ENABLED, DEFAULT_RENDERER_ENABLED)
            .putBoolean(PREF_LANGUAGE_ENABLED, DEFAULT_LANGUAGE_ENABLED)
            .putBoolean(PREF_BOUNCE_ENABLED, DEFAULT_BOUNCE_ENABLED)
            .putInt(PREF_REGULAR_STRENGTH, DEFAULT_REGULAR_STRENGTH)
            .putInt(PREF_HOLD_STRENGTH, DEFAULT_HOLD_STRENGTH)
            .putInt(PREF_RENDERER_STRENGTH, DEFAULT_RENDERER_STRENGTH)
            .putInt(PREF_LANGUAGE_STRENGTH, DEFAULT_LANGUAGE_STRENGTH)
            .putInt(PREF_BOUNCE_STRENGTH, DEFAULT_BOUNCE_STRENGTH)
            .putInt(PREF_BOUNCE_RETURN_STRENGTH, DEFAULT_BOUNCE_RETURN_STRENGTH)
            .apply()
    }

    private fun recentSurfaceHaptic(): Boolean {
        return SystemClock.uptimeMillis() - lastSurfaceHapticAtMs <= REGULAR_CLICK_SUPPRESS_WINDOW_MS
    }

    private fun hapticsEnabled(context: Context): Boolean {
        return context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, DEFAULT_ENABLED)
    }

    private fun categoryEnabled(context: Context, key: String, default: Boolean): Boolean {
        return hapticsEnabled(context) && context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getBoolean(key, default)
    }

    private fun strength(context: Context, key: String, default: Int, override: Int? = null): Float {
        val value = (override ?: context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getInt(key, default))
            .coerceIn(0, 100)
        return value / 100f
    }

    private fun scaled(
        context: Context,
        key: String,
        base: Int,
        min: Int,
        max: Int,
        default: Int,
        override: Int? = null
    ): Int {
        val s = strength(context, key, default, override)
        return (min + (max - min) * s).toInt().coerceIn(1, 255)
    }

    private fun vibrateOneShot(context: Context, durationMs: Long, amplitude: Int): Boolean {
        if (!hapticsEnabled(context)) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) return false
        val vibrator = getVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun vibratePattern(context: Context, timings: LongArray, amplitudes: IntArray): Boolean {
        if (!hapticsEnabled(context)) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) return false
        val vibrator = getVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
            true
        } catch (_: Exception) {
            val firstPulse = timings.firstOrNull { it > 0L } ?: 18L
            vibrateOneShot(context, firstPulse, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
