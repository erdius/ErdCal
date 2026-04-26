package com.example.helloworld

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

object NotificationScheduler {

    fun rescheduleAll(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val endMs = LocalDate.now().plusDays(31).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, now) }
            .also { ContentUris.appendId(it, endMs) }
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.HAS_ALARM
        )

        context.contentResolver.query(
            uri, projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(3) == 1
                // Schedule notifications for events with alarms, or all events if we want to ensure coverage
                val hasAlarm = c.getInt(4) == 1
                if (allDay || !hasAlarm) continue

                val eventId = c.getLong(0)
                val title = c.getString(1) ?: "(No title)"
                val startMs = c.getLong(2)
                if (startMs <= now) continue

                val reminderMinutes = queryReminderMinutes(context, eventId)
                scheduleEventNotifications(
                    context, eventId, title, startMs, reminderMinutes
                )
            }
        }
    }

    private fun queryReminderMinutes(context: Context, eventId: Long): Int? {
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return null
    }

    fun scheduleEventNotifications(
        context: Context,
        eventId: Long,
        title: String,
        startMs: Long,
        reminderMinutes: Int?
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        if (reminderMinutes != null && reminderMinutes > 0) {
            val reminderMs = startMs - reminderMinutes * 60_000L
            if (reminderMs > now) {
                val pi = buildPendingIntent(context, eventId, title, isReminder = true, reminderMinutes = reminderMinutes)
                scheduleExact(am, reminderMs, pi)
            }
        }

        if (startMs > now) {
            val pi = buildPendingIntent(context, eventId, title, isReminder = false, reminderMinutes = null)
            scheduleExact(am, startMs, pi)
        }
    }

    fun cancelEventNotifications(context: Context, eventId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(true, false).forEach { isReminder ->
            PendingIntent.getBroadcast(
                context,
                pendingIntentId(eventId, isReminder),
                Intent(context, NotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { pi ->
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun buildPendingIntent(
        context: Context,
        eventId: Long,
        title: String,
        isReminder: Boolean,
        reminderMinutes: Int?
    ): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_IS_REMINDER, isReminder)
            reminderMinutes?.let { putExtra(NotificationReceiver.EXTRA_REMINDER_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            pendingIntentId(eventId, isReminder),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleExact(am: AlarmManager, triggerMs: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun pendingIntentId(eventId: Long, isReminder: Boolean): Int =
        (eventId.toInt() shl 1) or (if (isReminder) 1 else 0)
}
