package com.popovicialinc.gama

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BackupHelper
 *
 * Serialises every GAMA preference into a JSON string (for export) and
 * deserialises a JSON string back into SharedPreferences (for import).
 *
 * File naming convention: GAMA_backup_YYYY-MM-DD.json
 * so users can keep multiple dated backups and instantly recognise them.
 */
object BackupHelper {

    // ── All keys that are backed up ───────────────────────────────────────────
    private val INT_KEYS = listOf(
        "animation_level", "particle_speed", "particle_parallax_sensitivity",
        "particle_count", "particle_count_custom", "theme_preference",
        "custom_accent", "custom_gradient_start", "custom_gradient_end",
        "ui_scale", "oled_accent_color", "notif_interval_idx"
    )
    private val BOOLEAN_KEYS = listOf(
        "gradient_enabled", "particles_enabled", "particle_parallax_enabled",
        "particle_star_mode", "particle_time_mode", "blur_enabled", "blur_optimised",
        "use_dynamic_color", "verbose_mode", "aggressive_mode",
        "oled_mode", "use_dynamic_color_oled", "dismiss_on_click_outside",
        "notif_enabled", "doze_mode"
    )
    private val FLOAT_KEYS = listOf("time_offset_hours")
    private val LONG_KEYS  = listOf("notif_last_sent")
    private val STRING_KEYS = listOf("user_name", "last_renderer")
    private val STRING_SET_KEYS = listOf("excluded_apps")

    /** Generate a timestamped filename like "GAMA_backup_2025-06-14.json" */
    fun buildFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "GAMA_backup_$date.json"
    }

    /**
     * Export — read every key from [prefs] and return a compact JSON string.
     * Runs on [Dispatchers.IO] so SharedPreferences reads never block the main thread.
     * @throws Exception if serialisation fails.
     */
    suspend fun export(prefs: SharedPreferences): String = withContext(Dispatchers.IO) {
        val root = JSONObject()

        // Version stamp — lets future GAMA versions handle schema migrations
        root.put("gama_backup_version", 1)
        root.put("exported_at", System.currentTimeMillis())

        INT_KEYS.forEach    { key -> if (prefs.contains(key)) root.put(key, prefs.getInt(key, 0)) }
        BOOLEAN_KEYS.forEach{ key -> if (prefs.contains(key)) root.put(key, prefs.getBoolean(key, false)) }
        FLOAT_KEYS.forEach  { key -> if (prefs.contains(key)) root.put(key, prefs.getFloat(key, 0f).toDouble()) }
        LONG_KEYS.forEach   { key -> if (prefs.contains(key)) root.put(key, prefs.getLong(key, 0L)) }
        STRING_KEYS.forEach { key -> if (prefs.contains(key)) root.put(key, prefs.getString(key, "") ?: "") }

        STRING_SET_KEYS.forEach { key ->
            val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
            val arr = JSONArray()
            set.forEach { arr.put(it) }
            root.put(key, arr)
        }

        root.toString(2) // pretty-print with 2-space indent
    }

    /**
     * Import — parse [json] and write every recognised key back into [prefs].
     * Unknown keys are silently ignored so forward-compatible backups work.
     * Runs on [Dispatchers.IO] so JSON parsing and SharedPreferences writes
     * never block the main thread.
     * @return a human-readable summary like "Restored 24 settings."
     * @throws Exception if the JSON is malformed or has wrong backup version.
     */
    suspend fun import(prefs: SharedPreferences, json: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject(json)

        // Reject completely foreign files
        if (root.has("gama_backup_version").not() || root.optInt("gama_backup_version", 0) < 1) {
            throw IllegalArgumentException("This doesn't look like a GAMA backup file.")
        }

        var count = 0
        val editor = prefs.edit()

        INT_KEYS.forEach { key ->
            if (root.has(key)) { editor.putInt(key, root.getInt(key)); count++ }
        }
        BOOLEAN_KEYS.forEach { key ->
            if (root.has(key)) { editor.putBoolean(key, root.getBoolean(key)); count++ }
        }
        FLOAT_KEYS.forEach { key ->
            if (root.has(key)) { editor.putFloat(key, root.getDouble(key).toFloat()); count++ }
        }
        LONG_KEYS.forEach { key ->
            if (root.has(key)) { editor.putLong(key, root.getLong(key)); count++ }
        }
        STRING_KEYS.forEach { key ->
            if (root.has(key)) { editor.putString(key, root.getString(key)); count++ }
        }
        STRING_SET_KEYS.forEach { key ->
            if (root.has(key)) {
                val arr = root.getJSONArray(key)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                editor.putStringSet(key, set)
                count++
            }
        }

        editor.apply()
        "Restored $count settings successfully."
    }
}
