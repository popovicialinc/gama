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
 * GAMA OpenGL Quick Settings Tile
 *
 * Dedicated tile that forces OpenGL renderer immediately on tap.
 * Active (highlighted) when OpenGL is the current renderer.
 *
 * Register in AndroidManifest.xml:
 *
 *   <service
 *       android:name=".OpenGLTileService"
 *       android:exported="true"
 *       android:label="GAMA · OpenGL"
 *       android:icon="@drawable/ic_tile"
 *       android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *   </service>
 */
@RequiresApi(Build.VERSION_CODES.N)
class OpenGLTileService : TileService() {

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
            setTile(Tile.STATE_INACTIVE, "Shizuku not running")
            return
        }
        if (!ShizukuHelper.checkPermission()) {
            setTile(Tile.STATE_INACTIVE, "Permission needed")
            return
        }

        setTile(Tile.STATE_ACTIVE, "Switching…")

        val prefs      = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val aggressive = prefs.getBoolean("aggressive_mode", false)
        val excluded   = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()

        scope.launch {
            try {
                ShizukuHelper.runOpenGLSuspend(
                    context        = applicationContext,
                    aggressiveMode = aggressive,
                    excludedApps   = excluded,
                    targetedApps   = emptySet(),
                    onStatusUpdate = {}
                )
                prefs.edit().putString("last_renderer", "OpenGL").apply()
                setTile(Tile.STATE_ACTIVE, null)
            } catch (e: Exception) {
                setTile(Tile.STATE_INACTIVE, "Failed — tap to retry")
            }
        }
    }

    private fun setTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.label = "GAMA · OpenGL"
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: when (state) {
                Tile.STATE_ACTIVE   -> "Active"
                else                -> "Tap to enable"
            }
        }
        tile.updateTile()
    }

    private fun refreshTile() {
        val renderer = getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
            .getString("last_renderer", "OpenGL") ?: "OpenGL"
        setTile(
            state    = if (renderer == "OpenGL") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            subtitle = null
        )
    }
}
