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
                context.getString(R.string.restart_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.restart_channel_description)
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
        val text = context.getString(
            if (cameraRequiresTap) {
                R.string.restart_notification_camera
            } else {
                R.string.restart_notification_generic
            }
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.restart_notification_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.restart_action_resume),
                    startIntent
                ).build()
            )
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
