package com.popovicialinc.gama

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    fun runCommand(cmd: String): String {
        return try {
            val cls = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remoteProcess = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            val process = remoteProcess as? Process ?: return "Error: Could not cast to Process"

            // Read stdout and stderr sequentially on the calling thread.
            // Previously this spawned two raw Thread objects per command — with
            // aggressive mode force-stopping 200+ packages that meant hundreds of
            // short-lived threads.  Sequential reads are safe here because:
            //   (a) runCommand is always called from Dispatchers.IO so blocking is fine
            //   (b) the shell command has already finished (or timed out) before we
            //       read — there is no deadlock risk since we don't write to stdin.
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            val error  = process.errorStream.bufferedReader().readText()
            process.destroy()

            val exitCode = if (finished) process.exitValue() else -1
            if (!finished)                                "Error: command timed out"
            else if (exitCode != 0 && error.isNotEmpty()) "Error: $error"
            else if (output.isEmpty())                    "Success"
            else output.trim()
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

    suspend fun getCurrentRenderer(): String = withContext(Dispatchers.IO) {
        if (!checkBinder() || !checkPermission()) return@withContext "Unknown"
        val result = runCommand("getprop debug.hwui.renderer")
        when {
            result.contains("skiavk", ignoreCase = true) -> "Vulkan"
            result.contains("opengl", ignoreCase = true) -> "OpenGL"
            result.startsWith("Error")                   -> "Unknown"
            else                                         -> "Default"
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
            listOf(
                "am crash com.android.systemui",
                "am force-stop com.android.settings",
                "am force-stop com.sec.android.app.launcher",
                "am force-stop com.samsung.android.app.aodservice",
                "am crash com.google.android.inputmethod.latin"
            ).forEach { cmd ->
                onVerboseOutput?.invoke("Running: $cmd\n")
                runCommand(cmd).also { onVerboseOutput?.invoke("Output: $it\n\n") }
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
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = switchRendererSuspend("skiavk", "Vulkan", context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)

    suspend fun runOpenGLSuspend(
        context: Context,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = switchRendererSuspend("opengl", "OpenGL", context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)

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

    fun runVulkan(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        excludedApps: Set<String>, targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) { Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show(); return }
        if (!checkPermission()) { requestPermissionFallback(context); return }
        scope.launch { runVulkanSuspend(context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput) }
    }

    fun runOpenGL(
        context: Context, scope: CoroutineScope, aggressiveMode: Boolean,
        excludedApps: Set<String>, targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) { Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show(); return }
        if (!checkPermission()) { requestPermissionFallback(context); return }
        scope.launch { runOpenGLSuspend(context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput) }
    }

    fun applyCustomRenderers(
        context: Context, scope: CoroutineScope,
        customRendererApps: Map<String, String>,
        onStatusUpdate: (String) -> Unit, onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) { Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show(); return }
        if (!checkPermission()) { requestPermissionFallback(context); return }
        scope.launch { applyCustomRenderersSuspend(context, customRendererApps, onStatusUpdate, onVerboseOutput) }
    }

    fun getAllInstalledPackages(): List<String> {
        if (!checkBinder() || !checkPermission()) return emptyList()
        return runCommand("pm list packages").split("\n")
            .filter { it.startsWith("package:") }.map { it.substring(8).trim() }
            .filter { it.isNotEmpty() }.sorted()
    }

    // ── Crash log fetching ────────────────────────────────────────────────────

    suspend fun fetchCrashLogs(): List<CrashEntry> = withContext(Dispatchers.IO) {
        if (!checkBinder() || !checkPermission()) return@withContext emptyList()
        val raw = runCommand("dumpsys dropbox --print")
        if (raw.startsWith("Error") || raw.isEmpty()) return@withContext emptyList()
        parseCrashLogs(raw)
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
                NotificationCompat.Builder(context, "gama_test")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(if (userName.isNotEmpty()) "Hey $userName! 👋" else "Test Notification")
                    .setContentText(if (userName.isNotEmpty()) "Your notification system is working perfectly!" else "Notifications are working correctly!")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setVibrate(longArrayOf(0, 250, 250, 250))
                    .build()
            )
            true
        } catch (_: Exception) { false }
    }
}
