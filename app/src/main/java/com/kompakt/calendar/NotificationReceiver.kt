package com.kompakt.calendar

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kompaktcalendar.R

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "event_reminders"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_MS = "start_ms"
        const val EXTRA_END_MS = "end_ms"
        const val EXTRA_IS_REMINDER = "is_reminder"
        const val EXTRA_REMINDER_MINUTES = "reminder_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        var eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        var title = intent.getStringExtra(EXTRA_TITLE)
        var startMs = intent.getLongExtra(EXTRA_START_MS, -1L)
        var endMs = intent.getLongExtra(EXTRA_END_MS, -1L)
        var isReminder = intent.getBooleanExtra(EXTRA_IS_REMINDER, false)
        var reminderMinutes = if (intent.hasExtra(EXTRA_REMINDER_MINUTES)) {
            intent.getIntExtra(EXTRA_REMINDER_MINUTES, 0)
        } else null

        // If triggered by system EVENT_REMINDER broadcast
        if (eventId == -1L && "android.intent.action.EVENT_REMINDER" == intent.action) {
            eventId = intent.getLongExtra("id", -1L)
            if (eventId == -1L) return

            // Query basic info if missing
            val projection = arrayOf(
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Events.ALL_DAY
            )
            context.contentResolver.query(
                android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, eventId),
                projection, null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    title = c.getString(0)
                    // If it's a system broadcast, we treat it as a reminder if it's not starting exactly now?
                    // Actually, the system sends this for both.
                    isReminder = true 
                }
            }
        }

        if (eventId == -1L) return
        val displayTitle = title ?: "(No title)"
        val displayIsReminder = isReminder
        val displayMinutes = reminderMinutes ?: 0

        val contentText = if (displayIsReminder) {
            when {
                displayMinutes >= 60 * 24 -> {
                    val days = displayMinutes / (60 * 24)
                    "In $days ${if (days == 1) "day" else "days"}"
                }
                displayMinutes >= 60 -> {
                    val hours = displayMinutes / 60
                    val mins = displayMinutes % 60
                    if (mins > 0) "In ${hours}h ${mins}min" else "In ${hours}h"
                }
                displayMinutes > 0 -> "In ${displayMinutes}min"
                else -> "Starting now"
            }
        } else {
            "Starting now"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("event_id", eventId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(eventId, displayIsReminder),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertIntent = Intent(context, EventAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("event_id", eventId)
            if (startMs != -1L) putExtra("start_ms", startMs)
            if (endMs != -1L) putExtra("end_ms", endMs)
        }
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            notificationId(eventId, displayIsReminder) + 100,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("NotificationReceiver", "POST_NOTIFICATIONS not granted; skipping notify for event $eventId")
            return
        }

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(eventId, displayIsReminder), notification)
        } catch (e: SecurityException) {
            Log.w("NotificationReceiver", "Failed to post notification", e)
        }
    }
}

fun notificationId(eventId: Long, isReminder: Boolean): Int {
    val base = (eventId xor (eventId ushr 32)).toInt() and 0x7FFFFFFE
    return base or (if (isReminder) 1 else 0)
}
