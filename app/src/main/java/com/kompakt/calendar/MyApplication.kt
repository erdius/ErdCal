package com.kompakt.calendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.kompakt.calendar.calendar.CalendarRepository
import com.kompakt.calendar.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rescheduleJob: Job? = null

    fun registerCalendarObserver() {
        if (isObserverRegistered) return
        try {
            contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        calendarRepository.notifyChanged()
                        // Debounce reschedules while a sync is fanning out many onChange events.
                        rescheduleJob?.cancel()
                        rescheduleJob = appScope.launch {
                            delay(1500)
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
