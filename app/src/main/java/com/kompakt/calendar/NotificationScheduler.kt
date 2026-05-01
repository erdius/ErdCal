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
                val startMs = c.getLong(2)
                val endMsRow = c.getLong(5)
                
                if (startMs <= now) continue

                scheduledEventIds.add(eventId)

                val reminderMinutes = queryReminderMinutes(context, eventId)
                scheduleEventNotifications(
                    context, eventId, title, startMs, endMsRow, reminderMinutes
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
        endMs: Long,
        reminderMinutes: Int?
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        if (reminderMinutes != null) {
            val reminderMs = startMs - reminderMinutes * 60_000L
            if (reminderMs > now) {
                val pi = buildPendingIntent(context, eventId, title, startMs, endMs, isReminder = true, reminderMinutes = reminderMinutes)
                scheduleExact(am, reminderMs, pi)
            }
        }

        if (startMs > now) {
            val pi = buildPendingIntent(context, eventId, title, startMs, endMs, isReminder = false, reminderMinutes = null)
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

    private fun pendingIntentId(eventId: Long, isReminder: Boolean): Int {
        // Use full 64-bit hash, then carve one bit for the reminder/start flag.
        val base = (eventId xor (eventId ushr 32)).toInt() and 0x7FFFFFFE
        return base or (if (isReminder) 1 else 0)
    }
}
