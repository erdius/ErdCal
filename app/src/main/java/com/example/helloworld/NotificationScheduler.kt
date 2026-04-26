package com.example.helloworld

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationScheduler {

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
