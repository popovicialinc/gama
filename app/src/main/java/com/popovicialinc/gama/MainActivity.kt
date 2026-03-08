package com.popovicialinc.gama

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    // ── Shizuku listeners ─────────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (!Shizuku.isPreV11()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // Shizuku service died — nothing to do, binder listener will fire again on reconnect
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                // Restart the app so all Shizuku-dependent UI initialises cleanly
                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (intent != null) {
                    startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }
        }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        try { installSplashScreen() } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        // Register Shizuku listeners as early as possible
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        try {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Exception) {}

        setContent {
            // ── Notification permission ───────────────────────────────────────
            var notifPermTrigger by remember { mutableStateOf(0) }
            val notifPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* GamaUI re-checks hasPermission on recompose */ }
            LaunchedEffect(notifPermTrigger) {
                if (notifPermTrigger > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // ── Backup: SAF file create ───────────────────────────────────────
            var pendingBackupContent by remember { mutableStateOf<String?>(null) }
            val createDocLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                val content = pendingBackupContent ?: return@rememberLauncherForActivityResult
                uri?.let {
                    try {
                        contentResolver.openOutputStream(it)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                    } catch (_: Exception) {}
                }
                pendingBackupContent = null
            }

            // ── Restore: SAF file open ────────────────────────────────────────
            var pendingRestoreCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val openDocLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                val cb = pendingRestoreCallback ?: return@rememberLauncherForActivityResult
                uri?.let {
                    try {
                        val text = contentResolver.openInputStream(it)
                            ?.bufferedReader(Charsets.UTF_8)?.readText() ?: return@let
                        cb(text)
                    } catch (_: Exception) {}
                }
                pendingRestoreCallback = null
            }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                GamaUI(
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermTrigger++
                        }
                    },
                    onExportBackup = { jsonContent, fileName ->
                        pendingBackupContent = jsonContent
                        createDocLauncher.launch(fileName)
                    },
                    onImportBackup = { callback ->
                        pendingRestoreCallback = callback
                        openDocLauncher.launch(arrayOf("application/json"))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
