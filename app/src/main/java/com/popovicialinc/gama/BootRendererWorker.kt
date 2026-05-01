package com.popovicialinc.gama

import android.app.Notification
import android.content.Context
import android.app.PendingIntent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * BootRendererWorker
 *
 * Scheduled by BootReceiver via WorkManager — survives process death and has
 * no ANR deadline, unlike goAsync() which gets killed after ~10 s.
 *
 * Retry policy: exponential backoff starting at 30 s, up to 5 attempts.
 * This covers the common case where Shizuku takes 60–120 s to start after
 * boot (wireless-debugging handshake, SystemUI init, etc.).
 *
 * On each attempt:
 *  1. Poll for Shizuku for up to 90 s (2 s interval).
 *  2. If ready → setprop → notify success → return SUCCESS.
 *  3. If not ready after 90 s → return RETRY (WorkManager reschedules).
 *  4. After all retries exhausted WorkManager gives up → notify failure.
 *
 * We deliberately do NOT write "OpenGL" to prefs on failure so the UI keeps
 * showing the correct saved renderer rather than reverting unexpectedly.
 * The renderer pref is only corrected if we can actually verify via Shizuku
 * that the prop is still unset.
 */
class BootRendererWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_TAG = "gama_boot_renderer"
        // WorkManager retries until Result.failure() is returned.  We fire the
        // failure notification and stop retrying after this many attempts (0-indexed).
        private const val MAX_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val savedRenderer = prefs.getString("last_renderer", "OpenGL") ?: "OpenGL"

        val propValue = when (savedRenderer) {
            "Vulkan" -> "skiavk"
            "OpenGL" -> "opengl"
            else     -> return Result.success()  // unknown — nothing to do
        }

        // Poll up to 90 s on this attempt — longer than the old 60 s goAsync window
        // and within the WorkManager execution budget (10 min default).
        val shizukuReady = waitForShizuku(timeoutMs = 90_000L)

        if (!shizukuReady) {
            // Not ready yet — if we still have retries, WorkManager will reschedule.
            // Don't corrupt the prefs here; let the retry handle it.
            val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
            if (isLastAttempt) {
                // All retries exhausted — give up and notify.
                notifyBootResult(applicationContext, success = false, renderer = savedRenderer)
            }
            return if (isLastAttempt) Result.failure() else Result.retry()
        }

        // Shizuku binder responded, but give it a short settling window before
        // calling newProcess — the binder can ping OK ~300 ms before the remote
        // process spawner is actually accepting connections, causing setprop to
        // fail with a "broken pipe" error on the very first command.
        delay(500L)

        // Shizuku is ready — apply the prop.
        val result = ShizukuHelper.runCommand("setprop debug.hwui.renderer $propValue")
        val success = !result.startsWith("Error")

        return if (success) {
            prefs.edit()
                .putLong("last_switch_uptime", android.os.SystemClock.elapsedRealtime())
                .apply()
            notifyBootResult(applicationContext, success = true, renderer = savedRenderer)
            Result.success()
        } else {
            // setprop returned an error string — check if the prop is already correct
            // (some ROMs persist props across reboots).
            val current = ShizukuHelper.getCurrentRenderer()
            when {
                current == savedRenderer -> {
                    // Already set — nothing to do, just stamp the uptime and succeed silently.
                    prefs.edit()
                        .putLong("last_switch_uptime", android.os.SystemClock.elapsedRealtime())
                        .apply()
                    Result.success()
                }
                current == "Unknown" -> {
                    // Couldn't read the prop at all — Shizuku may still be settling.
                    // Don't treat this as a confirmed failure; retry so the next
                    // attempt can re-read once the binder is fully stable.
                    val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
                    if (isLastAttempt) {
                        notifyBootResult(applicationContext, success = false, renderer = savedRenderer)
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
                else -> {
                    // setprop genuinely failed and prop is confirmed wrong — retry.
                    val isLastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1
                    if (isLastAttempt) {
                        notifyBootResult(applicationContext, success = false, renderer = savedRenderer)
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
            }
        }
    }

    /**
     * Poll Shizuku every 2 seconds until binder is up and permission is granted,
     * or until [timeoutMs] elapses.
     */
    private suspend fun waitForShizuku(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()) return true
            delay(2_000L)
        }
        return false
    }

    private fun notifyBootResult(context: Context, success: Boolean, renderer: String) {
        if (!ShizukuHelper.hasNotificationPermission(context)) return

        val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notif_enabled", false)) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "gama_boot",
                    "GAMA Boot Status",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Renderer re-apply status after reboot" }
            )
        }

        val (title, body) = if (success) {
            "GAMA ✓  $renderer restored" to
                "$renderer renderer re-applied after reboot. Newly launched apps will use it."
        } else {
            "GAMA · $renderer restore skipped" to
                "Shizuku was not ready after boot. Open GAMA and switch manually when you want."
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        nm.notify(
            3001,
            android.app.Notification.Builder(context, "gama_boot")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }
}
