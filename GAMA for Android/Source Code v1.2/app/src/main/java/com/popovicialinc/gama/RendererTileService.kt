package com.popovicialinc.gama

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * GAMA Quick Settings Tile
 *
 * Displays the current renderer (Vulkan / OpenGL) in the notification shade
 * and toggles between them on tap — respecting the user's saved aggressive
 * mode and excluded apps settings from the main app.
 *
 * Requires Android 7.0+ (API 24). Register in AndroidManifest.xml:
 *
 *   <service
 *       android:name=".RendererTileService"
 *       android:exported="true"
 *       android:label="@string/tile_label"
 *       android:icon="@drawable/ic_tile"
 *       android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *   </service>
 */
@RequiresApi(Build.VERSION_CODES.N)
class RendererTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Tile lifecycle ────────────────────────────────────────────────────────

    /**
     * Called when the tile becomes visible in the panel.
     * Sync the tile label/state to whatever was last saved by the main app.
     */
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    /**
     * Called when the user taps the tile.
     * Reads current renderer from prefs, switches to the opposite one,
     * then updates the tile UI to reflect the new state.
     */
    override fun onClick() {
        super.onClick()

        if (!ShizukuHelper.checkBinder()) {
            updateTile(currentRenderer(), isActive = false, subtitle = "Shizuku not running")
            return
        }
        if (!ShizukuHelper.checkPermission()) {
            updateTile(currentRenderer(), isActive = false, subtitle = "Shizuku permission needed")
            return
        }

        val current  = currentRenderer()           // "Vulkan" | "OpenGL" | "Default"
        val switchTo = if (current == "Vulkan") "OpenGL" else "Vulkan"

        // Show switching state immediately so the tile feels responsive
        updateTile(switchTo, isActive = true, subtitle = "Switching…")

        val prefs        = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val aggressive   = prefs.getBoolean("aggressive_mode", false)
        val excludedApps = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()

        scope.launch {
            try {
                if (switchTo == "Vulkan") {
                    ShizukuHelper.runVulkanSuspend(
                        context       = applicationContext,
                        aggressiveMode = aggressive,
                        excludedApps  = excludedApps,
                        targetedApps  = emptySet(),
                        onStatusUpdate = { /* tile already shows state */ }
                    )
                } else {
                    ShizukuHelper.runOpenGLSuspend(
                        context       = applicationContext,
                        aggressiveMode = aggressive,
                        excludedApps  = excludedApps,
                        targetedApps  = emptySet(),
                        onStatusUpdate = { /* tile already shows state */ }
                    )
                }

                // Persist the new renderer so the main app and tile stay in sync
                prefs.edit().putString("last_renderer", switchTo).apply()
                updateTile(switchTo, isActive = switchTo == "Vulkan", subtitle = null)

            } catch (e: Exception) {
                // Revert tile label to what it was before
                updateTile(current, isActive = current == "Vulkan", subtitle = "Failed — tap to retry")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Read the last known renderer from shared prefs. */
    private fun currentRenderer(): String =
        getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getString("last_renderer", "OpenGL") ?: "OpenGL"

    /** Push a label + active/inactive state to the tile. */
    private fun updateTile(renderer: String, isActive: Boolean, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label    = "GAMA · $renderer"
        tile.state    = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        // Subtitle is API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: if (isActive) "Tap to switch to OpenGL"
                                        else          "Tap to switch to Vulkan"
        }

        tile.updateTile()
    }

    /** Sync tile to persisted state without doing any switching. */
    private fun refreshTile() {
        val renderer = currentRenderer()
        updateTile(
            renderer  = renderer,
            isActive  = renderer == "Vulkan",
            subtitle  = null
        )
    }
}
