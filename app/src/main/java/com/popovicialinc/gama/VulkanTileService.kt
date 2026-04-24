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
 * GAMA Vulkan Quick Settings Tile
 *
 * Dedicated tile that forces Vulkan renderer immediately on tap.
 * Active (highlighted) when Vulkan is the current renderer.
 *
 * Register in AndroidManifest.xml:
 *
 *   <service
 *       android:name=".VulkanTileService"
 *       android:exported="true"
 *       android:label=applicationContext.tileStr("tile","label","GAMA · Vulkan")
 *       android:icon="@drawable/ic_tile"
 *       android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *   </service>
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


class VulkanTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
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

        setTile(Tile.STATE_ACTIVE, applicationContext.tileStr("tile","state_switching","Switching…"))

        val prefs       = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val aggressive  = prefs.getBoolean("aggressive_mode", false)
        val killLauncher = prefs.getBoolean("kill_launcher", false)
        val excluded    = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()

        scope.launch {
            try {
                ShizukuHelper.runVulkanSuspend(
                    context        = applicationContext,
                    aggressiveMode = aggressive,
                    killLauncher   = killLauncher,
                    excludedApps   = excluded,
                    targetedApps   = emptySet(),
                    onStatusUpdate = {}
                )
                prefs.edit().putString("last_renderer", "Vulkan").apply()
                setTile(Tile.STATE_ACTIVE, null)
            } catch (e: Exception) {
                setTile(Tile.STATE_INACTIVE, applicationContext.tileStr("tile","state_failed","Failed — tap to retry"))
            }
        }
    }

    private fun setTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = applicationContext.tileStr("tile","label","GAMA · Vulkan")
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: when (state) {
                Tile.STATE_ACTIVE   -> applicationContext.tileStr("tile","state_active","Active")
                else                -> applicationContext.tileStr("tile","state_tap_to_enable","Tap to enable")
            }
        }
        tile.updateTile()
    }

    private fun refreshTile() {
        val renderer = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getString("last_renderer", "OpenGL") ?: "OpenGL"
        setTile(
            state    = if (renderer == "Vulkan") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            subtitle = null
        )
    }
}
