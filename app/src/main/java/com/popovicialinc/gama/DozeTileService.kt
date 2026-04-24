package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * GAMA Doze Quick Settings Tile
 *
 * Forces Android deep doze immediately on tap.
 * Uses the correct two-command sequence required on Samsung / Android 15+:
 *   Enable:  dumpsys battery unplug  →  dumpsys deviceidle force-idle
 *   Disable: dumpsys deviceidle unforce  →  dumpsys battery reset
 *
 * State is verified by actually querying the deviceidle deep state rather
 * than relying solely on SharedPreferences, so the tile stays accurate even
 * if the screen was turned on (which exits doze automatically).
 */
@RequiresApi(Build.VERSION_CODES.N)

// ── Tile localization helper ──────────────────────────────────────────────────
private fun android.content.Context.tileStr(section: String, key: String, fallback: String): String {
    return try {
        val prefs = getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE)
        val code = prefs.getString("selected_language", "en") ?: "en"
        if (code == "en") return fallback
        val raw = assets.open("translations/$code.json").bufferedReader().readText()
        org.json.JSONObject(raw).optJSONObject(section)?.optString(key)?.takeIf { it.isNotEmpty() } ?: fallback
    } catch (_: Exception) { fallback }
}


class DozeTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()

        if (!ShizukuHelper.checkBinder()) {
            setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile","state_shizuku_not_running","Shizuku not running"))
            return
        }
        if (!ShizukuHelper.checkPermission()) {
            setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile","state_permission_needed","Permission needed"))
            return
        }

        setTile(Tile.STATE_UNAVAILABLE, applicationContext.tileStr("tile","state_switching","Applying…"))

        scope.launch {
            try {
                val currentlyIdle = isActuallyIdle()

                if (currentlyIdle) {
                    // Exit doze: unforce first, then reset battery reporting
                    ShizukuHelper.runCommand("dumpsys deviceidle unforce")
                    ShizukuHelper.runCommand("dumpsys battery reset")
                    getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("doze_mode", false).apply()
                    setTile(Tile.STATE_INACTIVE, null)
                } else {
                    // Enter doze: unplug battery reporting first, then force-idle
                    // The battery unplug step is required on Samsung / Android 15+ —
                    // without it, force-idle silently does nothing.
                    ShizukuHelper.runCommand("dumpsys battery unplug")
                    ShizukuHelper.runCommand("dumpsys deviceidle force-idle")

                    // Give the system ~800 ms to transition state before verifying
                    kotlinx.coroutines.delay(800)

                    val succeeded = isActuallyIdle()
                    getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("doze_mode", succeeded).apply()

                    if (succeeded) {
                        setTile(Tile.STATE_ACTIVE, null)
                    } else {
                        // State didn't change — clean up battery reporting and report failure
                        ShizukuHelper.runCommand("dumpsys battery reset")
                        setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile","state_failed","Failed — screen on?"))
                    }
                }
            } catch (e: Exception) {
                setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile","state_failed","Error — tap to retry"))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Ask the system for the *actual* deep idle state rather than trusting prefs.
     * Returns true only if the device is genuinely in deep doze (IDLE).
     */
    private suspend fun isActuallyIdle(): Boolean {
        val result = ShizukuHelper.runCommand("dumpsys deviceidle get deep")
        return result.trim().equals("IDLE", ignoreCase = true)
    }

    private fun setTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = applicationContext.tileStr("tile","label","GAMA · Doze")
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: when (state) {
                Tile.STATE_ACTIVE      -> applicationContext.tileStr("tile","state_active","Deep doze active")
                Tile.STATE_UNAVAILABLE -> applicationContext.tileStr("common","working","Working…")
                else                   -> applicationContext.tileStr("tile","state_tap_to_enable","Tap to force doze")
            }
        }
        tile.updateTile()
    }

    /**
     * Refresh tile by checking real device state, not just prefs.
     * Also syncs prefs so they stay accurate for other parts of the app.
     */
    private suspend fun refreshTile() {
        val idle = if (ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()) {
            isActuallyIdle().also { actualIdle ->
                // Keep prefs in sync passively
                getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("doze_mode", actualIdle).apply()
            }
        } else {
            false
        }

        setTile(
            state    = if (idle) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            subtitle = null
        )
    }
}
