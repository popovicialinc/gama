package com.popovicialinc.gama

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tasker Integration via Broadcast Intents
 *
 * ─────────────────────────────────────────────────────────────────
 * HOW TO USE IN TASKER
 * ─────────────────────────────────────────────────────────────────
 * Action type: "Send Intent" (found under Misc category)
 * Package:     com.popovicialinc.gama
 * Class:       com.popovicialinc.gama.TaskerReceiver
 * Action:      com.popovicialinc.gama.ACTION_SET_RENDERER
 * Target:      Broadcast Receiver
 *
 * Extras (key:value):
 *   renderer : vulkan          → switch to Vulkan
 *   renderer : opengl          → switch to OpenGL
 *   aggressive : true/false    → (optional) enable aggressive mode, default false
 * ─────────────────────────────────────────────────────────────────
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SET_RENDERER = "com.popovicialinc.gama.ACTION_SET_RENDERER"
        const val EXTRA_RENDERER = "renderer"
        const val EXTRA_AGGRESSIVE = "aggressive"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_RENDERER) return

        val renderer = intent.getStringExtra(EXTRA_RENDERER)?.lowercase() ?: run {
            Toast.makeText(context, "GAMA: missing 'renderer' extra", Toast.LENGTH_SHORT).show()
            return
        }

        val aggressive = intent.getBooleanExtra(EXTRA_AGGRESSIVE, false)

        // Validate Shizuku before going async — fast checks, safe to do on main thread
        if (!ShizukuHelper.checkBinder()) {
            Toast.makeText(context, "GAMA: Shizuku not running!", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ShizukuHelper.checkPermission()) {
            Toast.makeText(context, "GAMA: Shizuku permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        // goAsync() keeps the BroadcastReceiver process alive until finish() is called.
        // Without it, Android can kill the process as soon as onReceive() returns,
        // cutting off the shell commands mid-execution.
        //
        // The CoroutineScope is tied directly to the pendingResult: the job is
        // cancelled in the finally block (via pendingResult.finish()), so rapid
        // repeated Tasker invocations never accumulate leaked coroutines.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                when (renderer) {
                    "vulkan" -> ShizukuHelper.runVulkanSuspend(
                        context = context,
                        aggressiveMode = aggressive,
                        excludedApps = emptySet(),
                        targetedApps = emptySet(),
                        onStatusUpdate = { status ->
                            Toast.makeText(context, "GAMA: $status", Toast.LENGTH_SHORT).show()
                        }
                    )

                    "opengl" -> ShizukuHelper.runOpenGLSuspend(
                        context = context,
                        aggressiveMode = aggressive,
                        excludedApps = emptySet(),
                        targetedApps = emptySet(),
                        onStatusUpdate = { status ->
                            Toast.makeText(context, "GAMA: $status", Toast.LENGTH_SHORT).show()
                        }
                    )

                    else -> Toast.makeText(
                        context,
                        "GAMA: unknown renderer '$renderer'. Use 'vulkan' or 'opengl'.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                // Always called — even if an exception is thrown — so Android
                // is always notified that the async work is truly complete.
                pendingResult.finish()
            }
        }
    }
}