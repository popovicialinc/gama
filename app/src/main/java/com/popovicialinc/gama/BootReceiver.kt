package com.popovicialinc.gama

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * BootReceiver
 *
 * Fires on BOOT_COMPLETED (and Xiaomi/HTC fast-boot equivalents).
 * Does exactly one thing: enqueues BootRendererWorker via WorkManager.
 *
 * Why WorkManager instead of goAsync():
 *  - goAsync() has a hard ~10 s deadline before Android kills the process.
 *    Shizuku typically takes 30–120 s to start after boot (wireless-debugging
 *    handshake, SystemUI init), so goAsync() was almost always dying before
 *    Shizuku became ready — hence the "wasn't ready in time" notification.
 *  - WorkManager jobs have no such deadline, survive process death, and
 *    support automatic retries with backoff.
 *
 * Retry policy:
 *  - Initial backoff: 30 s (gives Shizuku time to start on the first try).
 *  - Exponential backoff: 30 s → 60 s → 120 s → 240 s.
 *  - Max attempts: 5 (configured in BootRendererWorker).
 *  - Each attempt polls for Shizuku for up to 90 s before giving up that run.
 *
 * KEEP_EXISTING policy: if the device boots while a previous job is still
 * queued (e.g. very quick reboot), the old job wins so we never run two
 * simultaneous re-apply attempts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") return

        val prefs = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)

        // If the user never explicitly switched renderers, nothing to restore.
        val lastSwitchTime = prefs.getLong("last_switch_time", 0L)
        if (lastSwitchTime == 0L) return

        // Only bother if the saved renderer is Vulkan — OpenGL is the Android
        // default after a reboot anyway, so there's nothing to re-apply.
        val savedRenderer = prefs.getString("last_renderer", "OpenGL") ?: "OpenGL"
        if (savedRenderer == "OpenGL") return

        val workRequest = OneTimeWorkRequestBuilder<BootRendererWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS  // first retry after 30 s, then 60, 120, 240…
            )
            .addTag(BootRendererWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BootRendererWorker.WORK_TAG,
            ExistingWorkPolicy.KEEP,  // don't double-queue on quick reboots
            workRequest
        )
    }
}
