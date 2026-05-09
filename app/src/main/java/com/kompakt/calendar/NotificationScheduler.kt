package com.kompakt.calendar

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
            CalendarContract.Instances.HAS_ALARM,
            CalendarContract.Instances.END
        )

        val scheduledEventIds = mutableSetOf<Long>()

        context.contentResolver.query(
            uri, projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                // Only schedule the earliest upcoming instance for each event
                if (scheduledEventIds.contains(eventId)) continue

                val hasAlarm = c.getInt(4) == 1
                if (!hasAlarm) continue

                val title = c.getString(1) ?: "(No title)"
                val startMs = (c.getLong(2) / 60_000L) * 60_000L
                val endMsRow = (c.getLong(5) / 60_000L) * 60_000L
                
                if (startMs <= now) continue

                scheduledEventIds.add(eventId)

                val reminders = queryReminders(context, eventId)
                scheduleEventNotifications(
                    context, eventId, title, startMs, endMsRow, reminders
                )
            }
        }
    }

    private fun queryReminders(context: Context, eventId: Long): List<Int> {
        val list = mutableListOf<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                list.add(c.getInt(0))
            }
        }
        return list
    }

    fun scheduleEventNotifications(
        context: Context,
        eventId: Long,
        title: String,
        startMs: Long,
        endMs: Long,
        reminders: List<Int>
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val cleanStartMs = (startMs / 60_000L) * 60_000L
        val cleanEndMs = (endMs / 60_000L) * 60_000L

        reminders.forEach { mins ->
            val reminderMs = cleanStartMs - mins * 60_000L
            if (reminderMs > now) {
                val pi = buildPendingIntent(context, eventId, title, cleanStartMs, cleanEndMs, isReminder = true, reminderMinutes = mins)
                scheduleExact(am, reminderMs, pi)
            }
        }

        if (cleanStartMs > now) {
            val pi = buildPendingIntent(context, eventId, title, cleanStartMs, cleanEndMs, isReminder = false, reminderMinutes = null)
            scheduleExact(am, cleanStartMs, pi)
        }
    }

    fun scheduleSnooze(
        context: Context,
        eventId: Long,
        title: String,
        startMs: Long,
        endMs: Long,
        snoozeMinutes: Int
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerMs = ((System.currentTimeMillis() / 60_000L) + snoozeMinutes) * 60_000L
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_START_MS, (startMs / 60_000L) * 60_000L)
            putExtra(NotificationReceiver.EXTRA_END_MS, (endMs / 60_000L) * 60_000L)
            putExtra(NotificationReceiver.EXTRA_IS_REMINDER, true)
            putExtra(NotificationReceiver.EXTRA_REMINDER_MINUTES, snoozeMinutes)
            putExtra("is_snooze", true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            eventId.hashCode() * 31 * 31 + 31 + 1, // corresponds to notificationId(eventId, true, true)
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExact(am, triggerMs, pi)
    }

    fun cancelEventNotifications(context: Context, eventId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // We don't know the exact reminder minutes, but we can query them or just try common ones.
        // Better yet, query them from the DB.
        val reminders = queryReminders(context, eventId)
        reminders.forEach { mins ->
            PendingIntent.getBroadcast(
                context,
                pendingIntentId(eventId, true, mins),
                Intent(context, NotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { pi ->
                am.cancel(pi)
                pi.cancel()
            }
        }
        PendingIntent.getBroadcast(
            context,
            pendingIntentId(eventId, false, null),
            Intent(context, NotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { pi ->
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun buildPendingIntent(
        context: Context,
        eventId: Long,
        title: String,
        startMs: Long,
        endMs: Long,
        isReminder: Boolean,
        reminderMinutes: Int?
    ): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_START_MS, startMs)
            putExtra(NotificationReceiver.EXTRA_END_MS, endMs)
            putExtra(NotificationReceiver.EXTRA_IS_REMINDER, isReminder)
            reminderMinutes?.let { putExtra(NotificationReceiver.EXTRA_REMINDER_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            pendingIntentId(eventId, isReminder, reminderMinutes),
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

    private fun pendingIntentId(eventId: Long, isReminder: Boolean, reminderMinutes: Int?): Int {
        var h = eventId.hashCode()
        h = h * 31 + (if (isReminder) 1 else 0)
        h = h * 31 + 0 // isSnooze = false
        if (isReminder && reminderMinutes != null) {
            h = h * 31 + reminderMinutes
        }
        return h
    }
}

