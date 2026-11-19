package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.OrganizationRunEntity
import com.example.domain.RunStatus

object OrganizerNotificationHelper {
    const val CHANNEL_ID = "organizer_progress_channel"
    const val FOREGROUND_NOTIFICATION_ID = 4041
    const val COMPLETION_NOTIFICATION_ID = 4042

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun createOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 1001, intent, flags)
    }

    fun createServiceCancelIntent(context: Context): PendingIntent {
        val intent = Intent(context, OrganizerForegroundService::class.java).apply {
            action = OrganizerForegroundService.ACTION_CANCEL
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(context, 1002, intent, flags)
    }

    fun buildProgressNotification(
        context: Context,
        processed: Int,
        total: Int,
        contentTitle: String,
        contentText: String,
        cancelIntent: PendingIntent
    ): Notification {
        ensureChannel(context)
        val cancelLabel = context.getString(R.string.notification_action_cancel)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_organizer_notif)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(createOpenAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, cancelIntent)

        if (total > 0) {
            builder.setProgress(total, processed.coerceAtMost(total), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun updateProgressNotification(
        context: Context,
        processed: Int,
        total: Int,
        contentTitle: String,
        contentText: String,
        cancelIntent: PendingIntent
    ) {
        if (!hasPostNotificationPermission(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val notification = buildProgressNotification(
            context = context,
            processed = processed,
            total = total,
            contentTitle = contentTitle,
            contentText = contentText,
            cancelIntent = cancelIntent
        )
        runCatching { manager.notify(FOREGROUND_NOTIFICATION_ID, notification) }
    }

    fun showCompletionNotification(context: Context, run: OrganizationRunEntity) {
        if (!hasPostNotificationPermission(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val title = when (run.status) {
            RunStatus.COMPLETED -> "Organizer • Complete"
            RunStatus.COMPLETED_WITH_ERRORS -> "Organizer • Finished With Errors"
            RunStatus.HALTED_STORAGE -> "Organizer • Paused (Low Storage)"
            RunStatus.HALTED_PERMISSION -> "Organizer • Paused (Permission Lost)"
            RunStatus.CANCELLED -> "Organizer • Cancelled Safely"
            else -> "Organizer • Finished"
        }
        val summary = "Moved: ${run.movedCount} • Duplicates: ${run.duplicateCount} • Skipped: ${run.skippedCount} • Failed: ${run.failedCount}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_organizer_notif)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(createOpenAppIntent(context))
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(COMPLETION_NOTIFICATION_ID, notification) }
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
