package com.example.helloworld

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAll(context)
            } finally {
                result.finish()
            }
        }
    }

    private fun rescheduleAll(context: Context) {
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
                val hasAlarm = c.getInt(4) == 1
                if (allDay || !hasAlarm) continue

                val eventId = c.getLong(0)
                val title = c.getString(1) ?: "(No title)"
                val startMs = c.getLong(2)
                if (startMs <= now) continue

                val reminderMinutes = queryReminderMinutes(context, eventId)
                NotificationScheduler.scheduleEventNotifications(
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
}
