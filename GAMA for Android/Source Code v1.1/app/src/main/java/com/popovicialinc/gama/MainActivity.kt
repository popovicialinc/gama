package com.popovicialinc.gama

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private lateinit var permissionListener: Shizuku.OnRequestPermissionResultListener

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            installSplashScreen()
        } catch (e: Exception) {
            // Splash screen failed, continue anyway
        }

        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.apply {
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            // Window setup failed, continue anyway
        }

        // Initialize Shizuku listener safely
        try {
            permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) {
            // Shizuku not available, continue anyway
        }

        try {
            setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    GamaUI()
                }
            }
        } catch (e: Exception) {
            // Failed to set content
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            if (::permissionListener.isInitialized) {
                Shizuku.removeRequestPermissionResultListener(permissionListener)
            }
        } catch (e: Exception) {
            // Failed to remove listener
        }
    }
}