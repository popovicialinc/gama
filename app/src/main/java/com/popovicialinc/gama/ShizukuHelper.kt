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

    fun checkBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) { false }

    fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    fun runCommand(cmd: String): String {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val commandArray = arrayOf("sh", "-c", cmd)
            val remoteProcess = method.invoke(null, commandArray, null, null)
            val process = remoteProcess as? Process ?: return "Error: Could not cast to Process"

            // Read streams on separate threads so neither blocks the other
            // while we wait — otherwise a large stderr can deadlock waitFor().
            var output = ""
            var error  = ""
            val outThread = Thread { output = process.inputStream.bufferedReader().readText() }
            val errThread = Thread { error  = process.errorStream.bufferedReader().readText() }
            outThread.start(); errThread.start()

            // 10-second hard timeout — prevents an unresponsive Shizuku process
            // from blocking the IO dispatcher indefinitely.
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            outThread.join(500); errThread.join(500)
            process.destroy()

            if (!finished) "Error: command timed out"
            else if (error.isNotEmpty()) "Error: $error"
            else if (output.isEmpty()) "Success"
            else output.trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun getCurrentRenderer(): String {
        return withContext(Dispatchers.IO) {
            if (!checkBinder() || !checkPermission()) {
                return@withContext "Unknown"
            }

            val result = runCommand("getprop debug.hwui.renderer")

            when {
                result.contains("skiavk", ignoreCase = true) -> "Vulkan"
                result.contains("opengl", ignoreCase = true) -> "OpenGL"
                result.isEmpty() || result == "Success" -> "Default"
                result.contains("Error") -> "Unknown"
                else -> result.ifEmpty { "Not Set" }
            }
        }
    }

    // ============================================================================
    // SUSPEND IMPLEMENTATIONS
    // These do the actual work and can be awaited properly by any caller,
    // including TaskerReceiver. The public fun wrappers below call these
    // so that gama_ui.kt requires no changes.
    // ============================================================================

    /**
     * Suspend implementation for Vulkan switch. Runs entirely on IO dispatcher.
     * Returns when all commands have finished executing.
     */
    suspend fun runVulkanSuspend(
        context: Context,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatusUpdate("Running Vulkan commands...") }

        onVerboseOutput?.invoke("Running: setprop debug.hwui.renderer skiavk\n")
        runCommand("setprop debug.hwui.renderer skiavk").also { output ->
            onVerboseOutput?.invoke("Output: $output\n\n")
        }

        if (aggressiveMode) {
            onVerboseOutput?.invoke("Running aggressive mode - stopping all apps...\n")
            val packagesOutput = runCommand("pm list packages")
            val packages = packagesOutput.split("\n")
                .filter { it.startsWith("package:") }
                .map { it.substring(8) }
                .filter { pkg ->
                    !excludedApps.contains(pkg) &&
                            pkg != "com.popovicialinc.gama"
                }

            packages.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { output ->
                    onVerboseOutput?.invoke("Output: $output\n")
                }
            }
            onVerboseOutput?.invoke("\nStopped ${packages.size} apps\n\n")
        } else if (targetedApps.isNotEmpty()) {
            onVerboseOutput?.invoke("Running targeted mode - stopping ${targetedApps.size} selected apps...\n")
            targetedApps.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { output ->
                    onVerboseOutput?.invoke("Output: $output\n")
                }
            }
            onVerboseOutput?.invoke("\nStopped ${targetedApps.size} targeted apps\n\n")
        } else {
            val regularCommands = listOf(
                "am crash com.android.systemui",
                "am force-stop com.android.settings",
                "am force-stop com.sec.android.app.launcher",
                "am force-stop com.samsung.android.app.aodservice",
                "am crash com.google.android.inputmethod.latin"
            )
            regularCommands.forEach { cmd ->
                onVerboseOutput?.invoke("Running: $cmd\n")
                runCommand(cmd).also { output ->
                    onVerboseOutput?.invoke("Output: $output\n\n")
                }
            }
        }

        withContext(Dispatchers.Main) {
            onStatusUpdate("Vulkan commands executed!")
            Toast.makeText(context, "Switched to Vulkan", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Suspend implementation for OpenGL switch. Runs entirely on IO dispatcher.
     * Returns when all commands have finished executing.
     */
    suspend fun runOpenGLSuspend(
        context: Context,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatusUpdate("Running OpenGL commands...") }

        onVerboseOutput?.invoke("Running: setprop debug.hwui.renderer opengl\n")
        runCommand("setprop debug.hwui.renderer opengl").also { output ->
            onVerboseOutput?.invoke("Output: $output\n\n")
        }

        if (aggressiveMode) {
            onVerboseOutput?.invoke("Running aggressive mode - stopping all apps...\n")
            val packagesOutput = runCommand("pm list packages")
            val packages = packagesOutput.split("\n")
                .filter { it.startsWith("package:") }
                .map { it.substring(8) }
                .filter { pkg ->
                    !excludedApps.contains(pkg) &&
                            pkg != "com.popovicialinc.gama"
                }

            packages.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { output ->
                    onVerboseOutput?.invoke("Output: $output\n")
                }
            }
            onVerboseOutput?.invoke("\nStopped ${packages.size} apps\n\n")
        } else if (targetedApps.isNotEmpty()) {
            onVerboseOutput?.invoke("Running targeted mode - stopping ${targetedApps.size} selected apps...\n")
            targetedApps.forEach { pkg ->
                onVerboseOutput?.invoke("Stopping: $pkg\n")
                runCommand("am force-stop $pkg").also { output ->
                    onVerboseOutput?.invoke("Output: $output\n")
                }
            }
            onVerboseOutput?.invoke("\nStopped ${targetedApps.size} targeted apps\n\n")
        } else {
            val regularCommands = listOf(
                "am crash com.android.systemui",
                "am force-stop com.android.settings",
                "am force-stop com.sec.android.app.launcher",
                "am force-stop com.samsung.android.app.aodservice",
                "am crash com.google.android.inputmethod.latin"
            )
            regularCommands.forEach { cmd ->
                onVerboseOutput?.invoke("Running: $cmd\n")
                runCommand(cmd).also { output ->
                    onVerboseOutput?.invoke("Output: $output\n\n")
                }
            }
        }

        withContext(Dispatchers.Main) {
            onStatusUpdate("OpenGL commands executed!")
            Toast.makeText(context, "Switched to OpenGL", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Suspend implementation for custom per-app renderer settings.
     * Returns when all commands have finished executing.
     */
    suspend fun applyCustomRenderersSuspend(
        context: Context,
        customRendererApps: Map<String, String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatusUpdate("Applying custom renderer settings...") }

        onVerboseOutput?.invoke("=== Custom Renderer Mode ===\n")
        onVerboseOutput?.invoke("Configuring ${customRendererApps.size} apps with specific renderers\n\n")

        customRendererApps.forEach { (packageName, renderer) ->
            val rendererValue = when (renderer.lowercase()) {
                "vulkan" -> "skiavk"
                "opengl" -> "opengl"
                else -> return@forEach
            }

            onVerboseOutput?.invoke("Setting $packageName → $renderer\n")

            val cmd = "setprop debug.hwui.renderer.$packageName $rendererValue"
            onVerboseOutput?.invoke("Running: $cmd\n")
            runCommand(cmd).also { output ->
                onVerboseOutput?.invoke("Output: $output\n")
            }

            runCommand("am force-stop $packageName").also { output ->
                onVerboseOutput?.invoke("Force stopped: $output\n\n")
            }
        }

        withContext(Dispatchers.Main) {
            onStatusUpdate("Custom renderers applied!")
            Toast.makeText(context, "Applied ${customRendererApps.size} custom renderers", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================================
    // PUBLIC FUN WRAPPERS (unchanged API — gama_ui.kt needs no changes)
    // These simply delegate to the suspend implementations above.
    // ============================================================================

    fun runVulkan(
        context: Context,
        scope: CoroutineScope,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) {
            Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkPermission()) {
            Toast.makeText(context, "Requesting permission...", Toast.LENGTH_SHORT).show()
            Shizuku.requestPermission(0)
            return
        }

        scope.launch {
            runVulkanSuspend(context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)
        }
    }

    fun runOpenGL(
        context: Context,
        scope: CoroutineScope,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
        targetedApps: Set<String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) {
            Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkPermission()) {
            Shizuku.requestPermission(0)
            return
        }

        scope.launch {
            runOpenGLSuspend(context, aggressiveMode, excludedApps, targetedApps, onStatusUpdate, onVerboseOutput)
        }
    }

    fun applyCustomRenderers(
        context: Context,
        scope: CoroutineScope,
        customRendererApps: Map<String, String>,
        onStatusUpdate: (String) -> Unit,
        onVerboseOutput: ((String) -> Unit)? = null
    ) {
        if (!checkBinder()) {
            Toast.makeText(context, "Shizuku not running!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkPermission()) {
            Toast.makeText(context, "Requesting permission...", Toast.LENGTH_SHORT).show()
            Shizuku.requestPermission(0)
            return
        }

        scope.launch {
            applyCustomRenderersSuspend(context, customRendererApps, onStatusUpdate, onVerboseOutput)
        }
    }

    fun getAllInstalledPackages(): List<String> {
        return try {
            if (!checkBinder() || !checkPermission()) {
                return emptyList()
            }

            val output = runCommand("pm list packages")
            output.split("\n")
                .filter { it.startsWith("package:") }
                .map { it.substring(8).trim() }
                .filter { it.isNotEmpty() }
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============================================================================
    // NOTIFICATION HELPERS
    // ============================================================================

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "gama_test",
                "GAMA Test Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Test notifications from GAMA"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendTestNotification(context: Context, userName: String): Boolean {
        return try {
            if (!hasNotificationPermission(context)) {
                return false
            }

            createNotificationChannel(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(context, "gama_test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (userName.isNotEmpty()) "Hey $userName! 👋" else "Test Notification")
                .setContentText(if (userName.isNotEmpty()) "Your notification system is working perfectly!" else "Notifications are working correctly!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            true
        } catch (e: Exception) {
            false
        }
    }
}