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

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }

            process.waitFor()
            process.destroy()

            if (error.isNotEmpty()) "Error: $error"
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

    suspend fun checkVersion(currentVersion: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://raw.githubusercontent.com/popovicialinc/gama/main/version_android.txt")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val latestVersion = connection.getInputStream().bufferedReader().use {
                    it.readText().trim()
                }

                if (latestVersion.isEmpty()) {
                    return@withContext "Unable to check version"
                }

                // Compare versions
                val current = parseVersion(currentVersion)
                val latest = parseVersion(latestVersion)

                // Compare major, then minor, then patch
                val majorComparison = current.first.compareTo(latest.first)
                val minorComparison = current.second.compareTo(latest.second)
                val patchComparison = current.third.compareTo(latest.third)

                val result = when {
                    majorComparison != 0 -> majorComparison
                    minorComparison != 0 -> minorComparison
                    else -> patchComparison
                }

                when (result) {
                    -1 -> "Update available: v$latestVersion"
                    1 -> "You're ahead of release!"
                    else -> "You're up to date ✓"
                }
            } catch (e: Exception) {
                "Unable to check version: ${e.message}"
            }
        }
    }

    private fun parseVersion(version: String): Triple<Int, Int, Int> {
        return try {
            val parts = version.split(".")
            Triple(
                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                parts.getOrNull(2)?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            Triple(0, 0, 0)
        }
    }

    fun runVulkan(
        context: Context,
        scope: CoroutineScope,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
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

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStatusUpdate("Running Vulkan commands...") }

            val commands = mutableListOf<String>()

            // Set renderer property
            commands.add("setprop debug.hwui.renderer skiavk")
            onVerboseOutput?.invoke("Running: setprop debug.hwui.renderer skiavk\n")
            runCommand("setprop debug.hwui.renderer skiavk").also { output ->
                onVerboseOutput?.invoke("Output: $output\n\n")
            }

            if (aggressiveMode) {
                // Get all packages and force stop them except excluded ones
                onVerboseOutput?.invoke("Running aggressive mode - stopping all apps...\n")
                val packagesOutput = runCommand("pm list packages")
                val packages = packagesOutput.split("\n")
                    .filter { it.startsWith("package:") }
                    .map { it.substring(8) }
                    .filter { pkg ->
                        !excludedApps.contains(pkg) &&
                                pkg != "com.popovicialinc.gama" // Don't stop ourselves
                    }

                packages.forEach { pkg ->
                    onVerboseOutput?.invoke("Stopping: $pkg\n")
                    runCommand("am force-stop $pkg").also { output ->
                        onVerboseOutput?.invoke("Output: $output\n")
                    }
                }
                onVerboseOutput?.invoke("\nStopped ${packages.size} apps\n\n")
            } else {
                // Regular mode - only specific apps
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
    }

    fun runOpenGL(
        context: Context,
        scope: CoroutineScope,
        aggressiveMode: Boolean,
        excludedApps: Set<String>,
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

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStatusUpdate("Running OpenGL commands...") }

            // Set renderer property
            onVerboseOutput?.invoke("Running: setprop debug.hwui.renderer opengl\n")
            runCommand("setprop debug.hwui.renderer opengl").also { output ->
                onVerboseOutput?.invoke("Output: $output\n\n")
            }

            if (aggressiveMode) {
                // Get all packages and force stop them except excluded ones
                onVerboseOutput?.invoke("Running aggressive mode - stopping all apps...\n")
                val packagesOutput = runCommand("pm list packages")
                val packages = packagesOutput.split("\n")
                    .filter { it.startsWith("package:") }
                    .map { it.substring(8) }
                    .filter { pkg ->
                        !excludedApps.contains(pkg) &&
                                pkg != "com.popovicialinc.gama" // Don't stop ourselves
                    }

                packages.forEach { pkg ->
                    onVerboseOutput?.invoke("Stopping: $pkg\n")
                    runCommand("am force-stop $pkg").also { output ->
                        onVerboseOutput?.invoke("Output: $output\n")
                    }
                }
                onVerboseOutput?.invoke("\nStopped ${packages.size} apps\n\n")
            } else {
                // Regular mode - only specific apps
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

    /**
     * Check if notification permission is granted.
     * For Android 13+ (TIRAMISU), checks POST_NOTIFICATIONS permission.
     * For older versions, always returns true.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required on older versions
        }
    }

    /**
     * Create the notification channel for GAMA test notifications.
     * Must be called before sending any notifications.
     */
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

    /**
     * Send a test notification.
     * Returns true if successful, false otherwise.
     */
    fun sendTestNotification(context: Context, userName: String): Boolean {
        return try {
            // Check permission first
            if (!hasNotificationPermission(context)) {
                return false
            }

            // Create channel if needed
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