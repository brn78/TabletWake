package com.tabletwake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object RestartNotifier {
    private const val CHANNEL_ID = "wake_restart"
    private const val NOTIFICATION_ID = 42

    fun show(context: Context, cameraRequiresTap: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Riattivazione dopo il riavvio",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Richiede un tocco quando Android non consente il riavvio automatico"
            }
        )

        val startIntent = PendingIntent.getForegroundService(
            context,
            3,
            Intent(context, WakeMonitorService::class.java)
                .setAction(WakeMonitorService.ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (cameraRequiresTap) {
            "Tocca Riattiva: Android richiede una conferma per riaprire la fotocamera."
        } else {
            "Tocca Riattiva per ripristinare il rilevamento."
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Riattiva Tablet Wake")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Riattiva", startIntent).build())
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
