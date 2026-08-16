package com.tabletwake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

object WakeController {
    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager.isInteractive) return false

        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "TabletWake::ScreenWake"
        )

        wakeLock.acquire(3_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            if (wakeLock.isHeld) wakeLock.release()
        }, 900L)
        return true
    }
}
