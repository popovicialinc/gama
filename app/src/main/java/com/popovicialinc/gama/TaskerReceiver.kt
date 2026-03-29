package com.popovicialinc.gama

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 *
 * SECURITY — required AndroidManifest.xml entries:
 * ─────────────────────────────────────────────────────────────────
 * Without the permission declaration below any installed app can trigger
 * renderer switches.  Add both blocks to AndroidManifest.xml:
 *
 *   <!-- 1. Declare the permission (before <application>) -->
 *   <permission
 *       android:name="com.popovicialinc.gama.TASKER_PERMISSION"
 *       android:protectionLevel="normal" />
 *
 *   <!-- 2. Guard the receiver with it -->
 *   <receiver
 *       android:name=".TaskerReceiver"
 *       android:exported="true"
 *       android:permission="com.popovicialinc.gama.TASKER_PERMISSION">
 *       <intent-filter>
 *           <action android:name="com.popovicialinc.gama.ACTION_SET_RENDERER" />
 *       </intent-filter>
 *   </receiver>
 *
 * In Tasker: Settings → Misc → "External Access" must be enabled, and the
 * Tasker app must request TASKER_PERMISSION in its own manifest, or you can
 * grant it via ADB:
 *   adb shell pm grant net.dinglisch.android.taskerm \
 *       com.popovicialinc.gama.TASKER_PERMISSION
 * ─────────────────────────────────────────────────────────────────
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SET_RENDERER  = "com.popovicialinc.gama.ACTION_SET_RENDERER"
        const val EXTRA_RENDERER       = "renderer"
        const val EXTRA_AGGRESSIVE     = "aggressive"
        const val TASKER_PERMISSION    = "com.popovicialinc.gama.TASKER_PERMISSION"

        /** Package names that are allowed to send renderer-switch broadcasts. */
        private val TRUSTED_PACKAGES = setOf(
            "net.dinglisch.android.taskerm",   // Tasker (Play Store)
            "net.dinglisch.android.tasker",    // Tasker (direct / F-Droid)
            "com.twofortyfouram.locale",       // Locale (Tasker plugin host)
            "com.popovicialinc.gama"           // self (tile services, etc.)
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_RENDERER) return

        // ── Caller verification ───────────────────────────────────────────────
        // Primary defence: the manifest-level android:permission on the receiver
        // means Android rejects any sender that hasn't been granted TASKER_PERMISSION.
        //
        // Secondary defence (belt-and-suspenders): verify the calling UID belongs
        // to a known-trusted package.  callingUid is set by the OS and cannot be
        // spoofed by user-space code.
        //
        // Broadcasts sent from within the same process (e.g. tile services) have
        // callingUid == our own UID and always pass.
        val callingUid = android.os.Binder.getCallingUid()
        val ownUid     = android.os.Process.myUid()
        if (callingUid != ownUid) {
            val callerPackages = try {
                context.packageManager.getPackagesForUid(callingUid)?.toSet() ?: emptySet()
            } catch (_: Exception) { emptySet() }

            if (callerPackages.intersect(TRUSTED_PACKAGES).isEmpty()) {
                // Unknown caller — reject silently.  No Toast so we don't leak
                // information about the permission structure to the attacker.
                return
            }
        }

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