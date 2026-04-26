package com.example.helloworld

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.example.helloworld.calendar.CalendarRepository
import com.example.helloworld.data.UserPreferencesRepository

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
                "Calendar Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders and start notifications for calendar events"
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
                    }
                }
            )
            isObserverRegistered = true
        } catch (e: SecurityException) {
            // Permission not granted yet or revoked
        }
    }

}
