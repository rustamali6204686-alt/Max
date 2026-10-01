package com.max.assistant
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "max_boot"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        context
            .getSharedPreferences(
                "max",
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                "boot_pending",
                true
            )
            .apply()

        showBootNotification(context)
    }

    private fun showBootNotification(
        context: Context
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                context.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Max",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Max startup notification"
            }

            manager.createNotificationChannel(channel)
        }

        val launchIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(
                    context,
                    CHANNEL_ID
                )
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setContentTitle("Max")
                .setContentText(
                    "Tap to start Max after reboot."
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            manager.notify(
                NOTIFICATION_ID,
                notification
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
