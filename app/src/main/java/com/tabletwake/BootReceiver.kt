package com.tabletwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        AppUpdater.configurePeriodicChecks(context)
        if (!AppPreferences.monitoringEnabled(context)) return

        when (AppPreferences.mode(context)) {
            DetectionMode.PROXIMITY -> runCatching {
                WakeMonitorService.start(context, persist = false)
            }.onFailure {
                RestartNotifier.show(context, cameraRequiresTap = false)
            }

            DetectionMode.CAMERA -> RestartNotifier.show(context, cameraRequiresTap = true)
        }
    }
}
