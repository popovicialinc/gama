package com.popovicialinc.gama

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Data model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lightweight wrapper around a parsed translation JSON.
 * All lookups fall back to the English string when a key is missing,
 * so partially-translated community files never crash the app.
 */
class GamaStrings(private val json: JSONObject, private val fallback: JSONObject?) {

    /** Look up a nested key like "settings.title". Returns "" on miss. */
    fun get(section: String, key: String): String {
        return json.optJSONObject(section)?.optString(key)?.takeIf { it.isNotEmpty() }
            ?: fallback?.optJSONObject(section)?.optString(key)
            ?: ""
    }

    /** Convenience operators — `strings["settings.title"]` */
    operator fun get(dotKey: String): String {
        val parts = dotKey.split(".", limit = 2)
        return if (parts.size == 2) get(parts[0], parts[1]) else ""
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocal
// ─────────────────────────────────────────────────────────────────────────────

/** Provides [GamaStrings] to the entire composition tree. */
val LocalStrings = compositionLocalOf<GamaStrings> {
    error("LocalStrings not provided — wrap your root composable with GamaLocalizationProvider")
}

// ─────────────────────────────────────────────────────────────────────────────
// Language metadata  (parsed from meta block inside each JSON)
// ─────────────────────────────────────────────────────────────────────────────

data class LanguageEntry(
    val code: String,       // e.g. "en"
    val name: String,       // e.g. "English"
    val nativeName: String, // e.g. "English" / "Română"
    val flag: String,       // emoji flag
    val fileName: String    // asset file name, e.g. "en.json"
)

// ─────────────────────────────────────────────────────────────────────────────
// LocalizationManager  (singleton, loaded once per process)
// ─────────────────────────────────────────────────────────────────────────────

object LocalizationManager {

    private const val PREF_KEY = "selected_language"
    private const val DEFAULT_CODE = "en"
    private const val TRANSLATIONS_DIR = "translations"

    // Cached after first load
    private var cachedEnglish: JSONObject? = null
    private var cachedStrings: GamaStrings? = null
    private var cachedCode: String? = null
    private var cachedLanguages: List<LanguageEntry>? = null

    /** Load all available [LanguageEntry] objects from the translations asset folder. */
    suspend fun loadAvailableLanguages(context: Context): List<LanguageEntry> =
        withContext(Dispatchers.IO) {
            cachedLanguages?.let { return@withContext it }

            val assets = context.assets
            val files = try {
                assets.list(TRANSLATIONS_DIR) ?: emptyArray()
            } catch (_: Exception) { emptyArray<String>() }

            val entries = mutableListOf<LanguageEntry>()
            for (file in files.sorted()) {
                if (!file.endsWith(".json")) continue
                try {
                    val raw = assets.open("$TRANSLATIONS_DIR/$file")
                        .bufferedReader().readText()
                    val obj = JSONObject(raw)
                    val meta = obj.optJSONObject("meta") ?: continue
                    entries.add(
                        LanguageEntry(
                            code       = meta.optString("code", file.removeSuffix(".json")),
                            name       = meta.optString("name", file),
                            nativeName = meta.optString("nativeName", file),
                            flag       = meta.optString("flag", "🌐"),
                            fileName   = file
                        )
                    )
                } catch (_: Exception) { /* skip malformed files */ }
            }

            // Always ensure English is first
            val sorted = entries.sortedWith(compareBy { if (it.code == DEFAULT_CODE) "" else it.nativeName })
            cachedLanguages = sorted
            sorted
        }

    /** Load and return [GamaStrings] for the given language [code]. */
    suspend fun loadStrings(context: Context, code: String): GamaStrings =
        withContext(Dispatchers.IO) {
            // Return cache hit if the code matches what's already loaded
            if (code == cachedCode && cachedStrings != null) {
                return@withContext cachedStrings!!
            }

            // Always load English as fallback (cached after first load)
            val englishJson = cachedEnglish ?: run {
                val raw = try {
                    context.assets.open("$TRANSLATIONS_DIR/$DEFAULT_CODE.json")
                        .bufferedReader().readText()
                } catch (_: Exception) { "{}" }
                JSONObject(raw).also { cachedEnglish = it }
            }

            val targetJson: JSONObject = if (code == DEFAULT_CODE) {
                englishJson
            } else {
                val languages = loadAvailableLanguages(context)
                val entry = languages.firstOrNull { it.code == code }
                if (entry != null) {
                    try {
                        val raw = context.assets.open("$TRANSLATIONS_DIR/${entry.fileName}")
                            .bufferedReader().readText()
                        JSONObject(raw)
                    } catch (_: Exception) { englishJson }
                } else {
                    englishJson
                }
            }

            val strings = GamaStrings(
                json     = targetJson,
                fallback = if (code == DEFAULT_CODE) null else englishJson
            )
            cachedStrings = strings
            cachedCode = code
            strings
        }

    fun getSavedCode(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY, DEFAULT_CODE) ?: DEFAULT_CODE

    fun saveCode(prefs: SharedPreferences, code: String) =
        prefs.edit().putString(PREF_KEY, code).apply()

    /** Force-evict the strings cache so the next [loadStrings] call re-reads from disk. */
    fun invalidateCache() {
        cachedStrings = null
        cachedCode = null
        cachedLanguages = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider composable — wrap GamaUI with this
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Loads the user's selected language from assets and provides [GamaStrings]
 * via [LocalStrings] to all child composables.
 *
 * Usage in MainActivity:
 * ```kotlin
 * GamaLocalizationProvider(prefs = prefs) {
 *     GamaUI(...)
 * }
 * ```
 */
@Composable
fun GamaLocalizationProvider(
    prefs: SharedPreferences,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var strings by remember { mutableStateOf<GamaStrings?>(null) }
    var selectedCode by remember { mutableStateOf(LocalizationManager.getSavedCode(prefs)) }

    // Reload whenever selectedCode changes
    LaunchedEffect(selectedCode) {
        strings = LocalizationManager.loadStrings(context, selectedCode)
    }

    // While loading, fall back to a bare English stub so nothing crashes
    val resolved = strings ?: GamaStrings(JSONObject(), null)

    CompositionLocalProvider(LocalStrings provides resolved) {
        content()
    }
}
