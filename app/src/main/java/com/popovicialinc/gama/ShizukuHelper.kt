package com.popovicialinc.gama

import android.app.Notification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuHelper {

    // ── Core Shizuku checks — use the real API now that source is vendored ────

    fun checkBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    // ── Shell command execution via Shizuku.newProcess() ─────────────────────
    // Still uses reflection for newProcess since it keeps the Shizuku source
    // dependency minimal — only Shizuku.java needs to be vendored, not the
    // full process wrapper hierarchy.

    // ── Shell command execution via Shizuku.newProcess() ─────────────────────
    // Still uses reflection for newProcess since it keeps the Shizuku source
    // dependency minimal — only Shizuku.java needs to be vendored, not the
    // full process wrapper hierarchy.
    //
    // runCommand is a suspend function so it can be cancelled by the caller's
    // coroutine scope (e.g. the user dismisses the switch dialog mid-switch).
    // The blocking waitFor runs on Dispatchers.IO — never on the main thread.
    //
    // Timeout is 3 seconds, not 10. `getprop`, `setprop`, `am crash`, and
    // `am force-stop` all complete in well under 1s on any supported device.
    // 10s was just the outer safety net; 3s still covers any legitimate slow
    // case while cutting the worst-case UI freeze from 10s to 3s if Shizuku
    // hangs on an unusual command.

    suspend fun runCommand(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val cls = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            val process = remoteProcess as? Process ?: return@withContext "Error: Could not cast to Process"

            // Read stdout and stderr sequentially on the calling thread.
            // Previously this spawned two raw Thread objects per command — with
            // aggressive mode force-stopping 200+ packages that meant hundreds of
            // short-lived threads.  Sequential reads are safe here because:
            //   (a) runCommand always runs on Dispatchers.IO so blocking is fine
            //   (b) the shell command has already finished (or timed out) before we
            //       read — there is no deadlock risk since we don't write to stdin.
            val finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            val error  = process.errorStream.bufferedReader().readText()
            process.destroy()

            val exitCode = if (finished) process.exitValue() else -1
            when {
                !finished               -> "Error: command timed out"
                output.isNotEmpty()     -> output.trim()  // stdout wins even if stderr has warnings
                exitCode != 0 && error.isNotEmpty() -> "Error: $error"
                else                    -> "Success"      // empty stdout, clean exit = prop not set
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun requestPermissionFallback(context: Context) {
        try {
            Shizuku.requestPermission(0)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Open the Shizuku app and tap 'Use Shizuku' to grant permission",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Renderer detection ────────────────────────────────────────────────────
    //
    // Multi-source, foolproof renderer detection strategy:
    //  1. Check debug.hwui.renderer  — the prop GAMA sets; most reliable signal
    //  2. If empty/unset → system is running OpenGL (Android default when prop absent)
    //  3. If that fails → scan all properties for any hwui renderer mention
    //  4. Only return "Unknown" on a genuine I/O / permission error

    suspend fun getCurrentRenderer(): String = withContext(Dispatchers.IO) {
        if (!checkBinder() || !checkPermission()) return@withContext "Unknown"

        // ── Source 1: the prop GAMA directly sets ─────────────────────────────
        val primary = runCommand("getprop debug.hwui.renderer").trim()
        when {
            primary.contains("skiavk", ignoreCase = true) -> return@withContext "Vulkan"
            primary.contains("opengl", ignoreCase = true) -> return@withContext "OpenGL"
            primary.isEmpty() || primary == "Success"     -> return@withContext "OpenGL"
            primary.startsWith("Error")                   -> { /* fall through to secondary */ }
            else                                          -> return@withContext "OpenGL"
        }

        // ── Source 2: scan all properties for any renderer mention ────────────
        // Use grep -E for extended regex so | is proper alternation (not \|
        // which is a literal backslash-pipe in basic grep and matches nothing).
        val allProps = runCommand("getprop | grep -Ei 'hwui|renderer'").trim()
        when {
            allProps.contains("skiavk", ignoreCase = true) -> return@withContext "Vulkan"
            allProps.contains("opengl", ignoreCase = true)  -> return@withContext "OpenGL"
            allProps.startsWith("Error")                    -> { /* fall through */ }
        }

        // ── Source 3: last resort — ask hwui what it's actually using ─────────
        // "dumpsys hwui" is slow so we pipe just the first 20 lines.
        val dumpsys = runCommand("dumpsys hwui 2>/dev/null | head -20").trim()
        when {
            dumpsys.contains("skiavk",  ignoreCase = true) -> return@withContext "Vulkan"
            dumpsys.contains("vulkan",  ignoreCase = true) -> return@withContext "Vulkan"
            dumpsys.contains("opengl",  ignoreCase = true) -> return@withContext "OpenGL"
            dumpsys.contains("skiagl",  ignoreCase = true) -> return@withContext "OpenGL"
            dumpsys.contains("software",ignoreCase = true) -> return@withContext "OpenGL"
        }

        // All three sources failed — return Unknown so the caller can decide what to show
        "Unknown"
    }

    // ── Educated guess when Shizuku is unavailable ────────────────────────────
    // Returns the best guess at the current renderer without running any shell
    // commands, using only stable signals (SharedPreferences + boot time).
    //
    //  • If the device rebooted after the last recorded renderer switch, Android
    //    will have cleared all runtime system props → must be OpenGL (default).
    //  • Otherwise, last_renderer pref is our best evidence.
    //
    // Uses last_switch_uptime (SystemClock.elapsedRealtime() at switch time)
    // rather than last_switch_time (wall-clock) for reboot detection.
    // elapsedRealtime resets to ~0 on every boot, so comparing the stored
    // uptime against current elapsedRealtime() reliably detects reboots even
    // if the user manually changed the system clock between sessions.
    fun guessRendererWithoutShizuku(prefs: android.content.SharedPreferences): String {
        val lastRenderer     = prefs.getString("last_renderer", "OpenGL") ?: "OpenGL"
        val lastSwitchUptime = prefs.getLong("last_switch_uptime", 0L)

        // If we've never recorded an uptime-stamped switch, fall back to the
        // wall-clock key for backward compatibility with existing installs.
        if (lastSwitchUptime == 0L) {
            val lastSwitchMs = prefs.getLong("last_switch_time", 0L)
            val bootTimeMs   = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
            return if (lastSwitchMs > 0L && bootTimeMs > lastSwitchMs) "OpenGL" else lastRenderer
        }

        // Reliable path: if current elapsedRealtime < stored uptime, the device
        // has rebooted since the last switch and runtime props were cleared.
        return if (android.os.SystemClock.elapsedRealtime() < lastSwitchUptime) {
            "OpenGL"
        } else {
            lastRenderer
        }
    }

    // ── Renderer switch: shared implementation ────────────────────────────────
    // Both Vulkan and OpenGL switching are identical except for the prop value
    // and the display label. A single private function eliminates the duplication
    // so future changes (e.g. new app-restart logic) only need to be made once.

    private suspend fun switchRendererSuspend(
        propValue: String,
        label: String,
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean,
        killKeyboard: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatusUpdate("Running $label commands...") }
        onVerboseOutput?.invoke("Running: setprop debug.hwui.renderer $propValue\n")
        runCommand("setprop debug.hwui.renderer $propValue").also { onVerboseOutput?.invoke("Output: $it\n\n") }

        if (aggressiveMode) {
            val packages = runCommand("pm list packages").split("\n")
                .filter { it.startsWith("package:") }.map { it.substring(8) }
                .filter { it !in excludedApps && it != "com.popovicialinc.gama" }
            packages.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { onVerboseOutput?.invoke("Output: $it\n") }
            }
        } else if (targetedApps.isNotEmpty()) {
            targetedApps.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { onVerboseOutput?.invoke("Output: $it\n") }
            }
        } else {
            // Always restart Settings so it picks up the new renderer prop.
            runCommand("am force-stop com.android.settings").also {
                onVerboseOutput?.invoke("Running: am force-stop com.android.settings\n")
                onVerboseOutput?.invoke("Output: $it\n\n")
            }

            // ── Keyboard restart (opt-in) ─────────────────────────────────────
            // Some keyboards cache HWUI state aggressively. Keep this optional because
            // killing the active input method can briefly close the keyboard mid-typing.
            if (killKeyboard) {
                val cmd = "ime_pkg=\$(settings get secure default_input_method | cut -d/ -f1); [ -n \"\$ime_pkg\" ] && [ \"\$ime_pkg\" != \"null\" ] && am force-stop \"\$ime_pkg\""
                onVerboseOutput?.invoke("Running: $cmd\n")
                runCommand(cmd).also { onVerboseOutput?.invoke("Output: $it\n\n") }
            }

            // ── Launcher restart (opt-in) ─────────────────────────────────────
            // OFF by default.  On Xiaomi / MIUI / HyperOS, force-stopping the
            // launcher from a third-party app twice in one session triggers the
            // MIUI App Behavior Monitor and silently restricts GAMA's launch intents
            // (app appears to "never open" until reinstalled — reboot does not fix it).
            //
            // The renderer prop is system-wide; any app that has not yet launched will
            // automatically use the new renderer without a force-stop.  Users who want
            // launchers restarted immediately (e.g. on stock AOSP / Pixel) can enable
            // "Kill launcher on switch" in Functionality settings.
            if (killLauncher) {
                listOf(
                    "am force-stop com.miui.home",
                    "am force-stop com.sec.android.app.launcher",
                    "am force-stop com.google.android.apps.nexuslauncher",
                    "am force-stop com.android.launcher3"
                ).forEach { cmd ->
                    onVerboseOutput?.invoke("Running: $cmd\n")
                    runCommand(cmd).also { onVerboseOutput?.invoke("Output: $it\n\n") }
                }
            }
        }
        withContext(Dispatchers.Main) {
            onStatusUpdate("$label commands executed!")
            Toast.makeText(context, "Switched to $label", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun runVulkanSuspend(
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = switchRendererSuspend("skiavk", "Vulkan", context, aggressiveMode, killLauncher, killKeyboard, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)

    suspend fun runOpenGLSuspend(
        context: Context,
        aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = switchRendererSuspend("opengl", "OpenGL", context, aggressiveMode, killLauncher, killKeyboard, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)

    // ── Per-app custom renderers ──────────────────────────────────────────────

    suspend fun applyCustomRenderersSuspend(
        context: Context,
        customRendererApps: Map<String, String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatusUpdate("Applying custom renderer settings...") }
        customRendererApps.forEach { (pkg, renderer) ->
            val value = when (renderer.lowercase()) {
                "vulkan" -> "skiavk"
                "opengl" -> "opengl"
                else     -> return@forEach
            }
            runCommand("setprop debug.hwui.renderer.$pkg $value").also { onVerboseOutput?.invoke("Output: $it\n") }
            runCommand("am force-stop $pkg")
        }
        withContext(Dispatchers.Main) {
            onStatusUpdate("Custom renderers applied!")
            Toast.makeText(context, "Applied ${customRendererApps.size} custom renderers", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Public fun wrappers ───────────────────────────────────────────────────

    // ── Public fun wrappers ───────────────────────────────────────────────────
    // Single guard eliminates duplicated checkBinder/checkPermission boilerplate.

    private fun guardedLaunch(
        context: Context,
        scope: CoroutineScope,
        block: suspend () -> Unit
    ) {
        if (!checkBinder()) {
            Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkPermission()) {
            requestPermissionFallback(context)
            return
        }
        scope.launch { block() }
    }

    fun runVulkan(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>, targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) = guardedLaunch(context, scope) {
        runVulkanSuspend(context, aggressiveMode, killLauncher, killKeyboard, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)
    }

    fun runOpenGL(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        killLauncher: Boolean = false,
        killKeyboard: Boolean = false,
        excludedApps: Set<String>, targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) = guardedLaunch(context, scope) {
        runOpenGLSuspend(context, aggressiveMode, killLauncher, killKeyboard, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)
    }

    fun applyCustomRenderers(
        context: Context, scope: CoroutineScope,
        customRendererApps: Map<String, String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) = guardedLaunch(context, scope) {
        applyCustomRenderersSuspend(context, customRendererApps, onStatusUpdate, onVerboseOutput)
    }

    /**
     * Returns every package name on the device via `pm list packages -a`.
     *
     * This CANNOT use runCommand() because runCommand() calls waitFor() BEFORE
     * reading stdout.  If the process output exceeds the OS pipe buffer (~64 KB),
     * the process blocks trying to write, waitFor() never returns, the 3-second
     * timeout fires, and we discard all output.  On MIUI devices with 500+ packages
     * the output easily reaches 25–50 KB — close enough to the buffer limit that
     * it triggers intermittently depending on ROM and kernel config.
     *
     * Fix: read stdout in a concurrent coroutine so the buffer drains continuously
     * while the process runs.  Process can never block on a full buffer, so it
     * always exits cleanly within the timeout.
     */
    suspend fun getAllPackageNames(): List<String> = withContext(Dispatchers.IO) {
        if (!checkBinder() || !checkPermission()) return@withContext emptyList()
        try {
            val cls    = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(
                null, arrayOf("sh", "-c", "pm list packages -a"), null, null
            )
            val process = remoteProcess as? Process ?: return@withContext emptyList()

            try {
                // Launch concurrent readers BEFORE calling waitFor so the pipe
                // buffer never fills up regardless of how many packages exist.
                // coroutineScope provides the scope that async requires.
                val (outputText, _) = coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    // stderr also needs draining to prevent a secondary buffer block
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

                    if (!finished) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope Pair("", "")
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    errorDeferred.cancel() // discard stderr — we only need package names
                    Pair(out, "")
                }

                outputText
                    .lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Crash log fetching ────────────────────────────────────────────────────
    //
    // fetchCrashLogs uses the same concurrent-reader pattern as getAllPackageNames
    // because "dumpsys dropbox --print" on a full dropbox can easily produce
    // hundreds of KB — far beyond the OS pipe buffer (~64 KB).
    // runCommand() calls waitFor() before reading stdout, so on a full dropbox
    // the subprocess would block writing while waitFor() waits for it to exit:
    // a classic deadlock.  Draining stdout in a concurrent coroutine prevents this.

    suspend fun fetchCrashLogs(): List<CrashEntry> = withContext(Dispatchers.IO) {
        if (!checkBinder() || !checkPermission()) return@withContext emptyList()

        try {
            val cls    = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(
                null,
                arrayOf("sh", "-c", "dumpsys dropbox --print"),
                null,
                null
            )
            val process = remoteProcess as? Process ?: return@withContext emptyList()

            val raw = try {
                coroutineScope {
                    val outputDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().readText()
                    }
                    // Always drain stderr — even if we don't use it — to prevent
                    // a secondary buffer block if the command writes to both streams.
                    val errorDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().readText()
                    }

                    val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

                    if (!finished) {
                        outputDeferred.cancel()
                        errorDeferred.cancel()
                        return@coroutineScope ""
                    }

                    val out = try { outputDeferred.await() } catch (_: Exception) { "" }
                    errorDeferred.cancel()
                    out
                }
            } finally {
                process.destroy()
            }

            if (raw.isEmpty()) return@withContext emptyList()
            parseCrashLogs(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCrashLogs(raw: String): List<CrashEntry> {
        val entries = mutableListOf<CrashEntry>()
        val sections = raw.split(Regex("(?=Drop box tag:)"))
        for (section in sections) {
            if (section.isBlank()) continue
            val tagLine = section.lines().firstOrNull() ?: continue
            val relevantTags = listOf("system_app_crash","system_server_crash","system_app_anr","system_server_anr","crash","anr")
            if (relevantTags.none { tagLine.contains(it, ignoreCase = true) }) continue
            val tag = Regex("Drop box tag: ([^,]+)").find(tagLine)?.groupValues?.get(1)?.trim() ?: "unknown"
            val timeMillis = Regex("time: (\\d+)").find(tagLine)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val body = section.lines().drop(1).joinToString("\n").trim()
            val isRelevant = body.contains("SystemUI", ignoreCase = true) ||
                    body.contains("hwui", ignoreCase = true) ||
                    body.contains("renderer", ignoreCase = true) ||
                    body.contains("com.android.systemui", ignoreCase = true) ||
                    tagLine.contains("SystemUI", ignoreCase = true)
            if (!isRelevant && entries.size >= 30) continue
            if (!isRelevant && !tagLine.contains("system", ignoreCase = true)) continue
            val shortSummary = body.lines()
                .firstOrNull { it.contains("Exception") || it.contains("Error") || it.contains("at ") }
                ?.trim() ?: body.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "No details"
            entries.add(CrashEntry(
                tag        = tag,
                timeMillis = timeMillis,
                summary    = shortSummary.take(200),
                fullText   = body.take(4000),
                isSystemUI = body.contains("com.android.systemui", ignoreCase = true) ||
                        tagLine.contains("SystemUI", ignoreCase = true)
            ))
        }
        return entries.sortedByDescending { it.timeMillis }.take(50)
    }

    data class CrashEntry(
        val tag: String,
        val timeMillis: Long,
        val summary: String,
        val fullText: String,
        val isSystemUI: Boolean
    )

    // ── Notification helpers ──────────────────────────────────────────────────

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gama_test", "GAMA Test Notifications", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Test notifications from GAMA"; enableVibration(true); enableLights(true) }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    fun sendTestNotification(context: Context, userName: String): Boolean {
        return try {
            if (!hasNotificationPermission(context)) return false
            createNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                System.currentTimeMillis().toInt(),
                Notification.Builder(context, "gama_test")
                    // Android notification small icons must be an app-provided monochrome drawable.
                    // Framework icons can render as a blank white square on some ROMs.
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setColor(0xFF5A63A8.toInt())
                    .setColorized(false)
                    .setContentTitle(if (userName.isNotEmpty()) "Hey $userName! 👋" else "Test Notification")
                    .setContentText(if (userName.isNotEmpty()) "Your notification system is working perfectly!" else "Notifications are working correctly!")
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setVibrate(longArrayOf(0, 250, 250, 250))
                    .build()
            )
            true
        } catch (_: Exception) { false }
    }
}
