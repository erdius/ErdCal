package com.kompakt.calendar.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _changes = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 16)
    val changes = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    suspend fun getCalendars(): List<CalendarAccount> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val list = mutableListOf<CalendarAccount>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val accessLevel = c.getInt(8)
                list.add(
                    CalendarAccount(
                        id = c.getLong(0),
                        displayName = c.getString(1) ?: "",
                        accountName = c.getString(2) ?: "",
                        accountType = c.getString(3) ?: "",
                        ownerAccount = c.getString(4),
                        color = c.getInt(5),
                        isPrimary = c.getInt(6) == 1,
                        isVisible = c.getInt(7) == 1,
                        isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    )
                )
            }
        }
        return list
    }

    suspend fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        if (!hasWritePermission()) return
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.VISIBLE, if (visible) 1 else 0)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        resolver.update(uri, values, null, null)
        notifyChanged()
    }

    /**
     * Query event instances overlapping the given date range, expanding recurring events
     * via CalendarContract.Instances.
     */
    suspend fun getEventsBetween(
        start: LocalDate,
        endInclusive: LocalDate,
        onlyVisible: Boolean = true
    ): List<CalendarEvent> {
        if (!hasReadPermission()) return emptyList()

        val zone = ZoneId.systemDefault()
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMs)
        ContentUris.appendId(builder, endMs)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.HAS_ALARM,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.VISIBLE
        )

        val selection = if (onlyVisible) "${CalendarContract.Instances.VISIBLE} = 1" else null

        val list = mutableListOf<CalendarEvent>()
        resolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val allDay = c.getInt(7) == 1
                val beginMs = c.getLong(5)
                val endMsRow = c.getLong(6)
                val hasReminder = c.getInt(10) == 1
                
                var reminderMins: Int? = null
                if (hasReminder) {
                    resolver.query(
                        CalendarContract.Reminders.CONTENT_URI,
                        arrayOf(CalendarContract.Reminders.MINUTES),
                        "${CalendarContract.Reminders.EVENT_ID} = ?",
                        arrayOf(eventId.toString()),
                        null
                    )?.use { remC ->
                        if (remC.moveToFirst()) {
                            reminderMins = remC.getInt(0)
                        }
                    }
                }

                val startDt = if (allDay) {
                    // All-day events are stored in UTC at midnight
                    LocalDateTime.ofEpochSecond(beginMs / 1000, 0, ZoneOffset.UTC)
                } else {
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(beginMs), zone)
                }
                val endDt = if (allDay) {
                    LocalDateTime.ofEpochSecond(endMsRow / 1000, 0, ZoneOffset.UTC)
                } else {
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMsRow), zone)
                }
                list.add(
                    CalendarEvent(
                        id = eventId,
                        calendarId = c.getLong(1),
                        calendarDisplayName = c.getString(11) ?: "",
                        calendarColor = c.getInt(12),
                        title = c.getString(2) ?: "(No title)",
                        description = c.getString(3),
                        location = c.getString(4),
                        start = startDt,
                        end = endDt,
                        allDay = allDay,
                        rrule = c.getString(8),
                        timezone = c.getString(9),
                        hasReminder = hasReminder,
                        reminderMinutes = reminderMins
                    )
                )
            }
        }
        return list
    }

    suspend fun getEventsForMonth(month: YearMonth): List<CalendarEvent> =
        getEventsBetween(month.atDay(1), month.atEndOfMonth())

    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> =
        getEventsBetween(date, date)

    suspend fun getEventById(eventId: Long): CalendarEvent? {
        if (!hasReadPermission()) return null
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.HAS_ALARM,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.DURATION,
            CalendarContract.Events._ID
        )
        val list = mutableListOf<Int>()
        resolver.query(
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
        val firstReminder = list.firstOrNull()

        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val zone = ZoneId.systemDefault()
                val allDay = c.getInt(7) == 1
                val beginMs = c.getLong(5)
                val endMs = if (!c.isNull(6)) c.getLong(6) else beginMs + 60 * 60 * 1000
                val startDt = if (allDay)
                    LocalDateTime.ofEpochSecond(beginMs / 1000, 0, ZoneOffset.UTC)
                else
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(beginMs), zone)
                val endDt = if (allDay)
                    LocalDateTime.ofEpochSecond(endMs / 1000, 0, ZoneOffset.UTC)
                else
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMs), zone)
                return CalendarEvent(
                    id = c.getLong(0),
                    calendarId = c.getLong(1),
                    calendarDisplayName = c.getString(11) ?: "",
                    calendarColor = c.getInt(12),
                    title = c.getString(2) ?: "(No title)",
                    description = c.getString(3),
                    location = c.getString(4),
                    start = startDt,
                    end = endDt,
                    allDay = allDay,
                    rrule = c.getString(8),
                    timezone = c.getString(9),
                    hasReminder = c.getInt(10) == 1,
                    reminderMinutes = firstReminder
                )
            }
        }
        return null
    }

    data class NewEvent(
        val calendarId: Long,
        val title: String,
        val description: String?,
        val location: String?,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val allDay: Boolean,
        val reminderMinutes: Int? = null,
        val timezone: String = ZoneId.systemDefault().id
    )

    suspend fun insertEvent(event: NewEvent): Long? {
        if (!hasWritePermission()) return null
        val zone = ZoneId.systemDefault()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            event.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            if (event.allDay) {
                // All-day uses UTC midnight
                val startUtc = event.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endUtc = event.end.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.DTEND, endUtc)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, event.start.atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, event.end.atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone)
            }
        }

        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)

        if (event.reminderMinutes != null && !event.allDay) {
            val rv = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, event.reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
        }
        notifyChanged()
        return id
    }

    suspend fun updateEvent(eventId: Long, event: NewEvent): Boolean {
        if (!hasWritePermission()) return false
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description ?: "")
            put(CalendarContract.Events.EVENT_LOCATION, event.location ?: "")
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            if (event.allDay) {
                val startUtc = event.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endUtc = event.end.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.DTEND, endUtc)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, event.start.atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, event.end.atZone(zone).toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone)
            }
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rows = resolver.update(uri, values, null, null)

        // Replace reminders
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        if (event.reminderMinutes != null && !event.allDay) {
            val rv = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, event.reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
        }
        notifyChanged()
        return rows > 0
    }

    suspend fun deleteEvent(eventId: Long): Boolean {
        if (!hasWritePermission()) return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rows = resolver.delete(uri, null, null)
        notifyChanged()
        return rows > 0
    }

    suspend fun searchEvents(query: String, fromDaysBack: Long = 30, toDaysAhead: Long = 365): List<CalendarEvent> {
        if (query.isBlank()) return emptyList()
        val today = LocalDate.now()
        val all = getEventsBetween(today.minusDays(fromDaysBack), today.plusDays(toDaysAhead))
        val q = query.trim().lowercase()
        return all.filter {
            it.title.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true) ||
                    (it.location?.lowercase()?.contains(q) == true)
        }
    }
}
