package com.example.helloworld

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kompaktcalendar.R

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "calendar_events"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_REMINDER = "is_reminder"
        const val EXTRA_REMINDER_MINUTES = "reminder_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "(No title)"
        val isReminder = intent.getBooleanExtra(EXTRA_IS_REMINDER, false)
        val reminderMinutes = intent.getIntExtra(EXTRA_REMINDER_MINUTES, 0)

        val contentText = if (isReminder) {
            when {
                reminderMinutes >= 60 * 24 -> {
                    val days = reminderMinutes / (60 * 24)
                    "In $days ${if (days == 1) "day" else "days"}"
                }
                reminderMinutes >= 60 -> {
                    val hours = reminderMinutes / 60
                    val mins = reminderMinutes % 60
                    if (mins > 0) "In ${hours}h ${mins}min" else "In ${hours}h"
                }
                else -> "In ${reminderMinutes}min"
            }
        } else {
            "Starting now"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(notificationId(eventId, isReminder), notification)
    }
}

fun notificationId(eventId: Long, isReminder: Boolean): Int =
    (eventId.toInt() shl 1) or (if (isReminder) 1 else 0)
