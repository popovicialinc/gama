package com.popovicialinc.gama

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
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
            e.printStackTrace()
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
                e.printStackTrace()
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

    fun runVulkan(context: Context, scope: CoroutineScope, onStatusUpdate: (String) -> Unit) {
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

            listOf(
                "setprop debug.hwui.renderer skiavk",
                "am crash com.android.systemui",
                "am force-stop com.android.settings",
                "am force-stop com.sec.android.app.launcher",
                "am force-stop com.samsung.android.app.aodservice",
                "am crash com.google.android.inputmethod.latin"
            ).forEach { runCommand(it) }

            withContext(Dispatchers.Main) {
                onStatusUpdate("Vulkan commands executed!")
                Toast.makeText(context, "Switched to Vulkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runOpenGL(context: Context, scope: CoroutineScope, onStatusUpdate: (String) -> Unit) {
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

            listOf(
                "setprop debug.hwui.renderer opengl",
                "am crash com.android.systemui",
                "am force-stop com.android.settings",
                "am force-stop com.sec.android.app.launcher",
                "am force-stop com.samsung.android.app.aodservice",
                "am crash com.google.android.inputmethod.latin"
            ).forEach { runCommand(it) }

            withContext(Dispatchers.Main) {
                onStatusUpdate("OpenGL commands executed!")
                Toast.makeText(context, "Switched to OpenGL", Toast.LENGTH_SHORT).show()
            }
        }
    }
}