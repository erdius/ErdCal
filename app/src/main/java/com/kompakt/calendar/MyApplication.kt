package com.example.calendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.example.calendar.calendar.CalendarRepository
import com.example.calendar.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var calendarRepository: CalendarRepository
        private set
    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        calendarRepository = CalendarRepository(this)
        userPreferencesRepository = UserPreferencesRepository(this)
        createNotificationChannel()

        if (calendarRepository.hasReadPermission()) {
            registerCalendarObserver()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationReceiver.CHANNEL_ID,
                "Event Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders and start notifications for calendar events"
                enableVibration(true)
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private var isObserverRegistered = false

    fun registerCalendarObserver() {
        if (isObserverRegistered) return
        try {
            contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        calendarRepository.notifyChanged()
                        // Reschedule notifications when calendar data changes (e.g. sync finishes)
                        CoroutineScope(Dispatchers.IO).launch {
                            NotificationScheduler.rescheduleAll(this@MyApplication)
                        }
                    }
                }
            )
            isObserverRegistered = true
        } catch (e: SecurityException) {
            // Permission not granted yet or revoked
        }
    }

}
