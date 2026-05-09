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

        val rawRows = mutableListOf<EventRow>()
        resolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                rawRows.add(
                    EventRow(
                        eventId = c.getLong(0),
                        calendarId = c.getLong(1),
                        title = c.getString(2),
                        description = c.getString(3),
                        location = c.getString(4),
                        beginMs = c.getLong(5),
                        endMs = c.getLong(6),
                        allDay = c.getInt(7) == 1,
                        rrule = c.getString(8),
                        timezone = c.getString(9),
                        hasReminder = c.getInt(10) == 1,
                        calendarDisplayName = c.getString(11),
                        calendarColor = c.getInt(12)
                    )
                )
            }
        }

        // Batch reminder lookup: one query for all event IDs that have reminders.
        val remindEventIds = rawRows.filter { it.hasReminder }.map { it.eventId }.distinct()
        val reminderMap: Map<Long, List<Int>> = if (remindEventIds.isEmpty()) emptyMap()
        else queryRemindersIn(remindEventIds)

        val list = mutableListOf<CalendarEvent>()
        for (row in rawRows) {
            val eventId = row.eventId
            val allDay = row.allDay
            val beginMs = row.beginMs
            val endMsRow = row.endMs
            val hasReminder = row.hasReminder
            val reminders: List<Int> = if (hasReminder) reminderMap[eventId] ?: emptyList() else emptyList()

            val startDt = if (allDay) {
                // All-day events are stored in UTC at midnight
                LocalDateTime.ofEpochSecond(beginMs / 1000, 0, ZoneOffset.UTC)
            } else {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(beginMs), zone)
                    .withSecond(0).withNano(0)
            }
            val endDt = if (allDay) {
                LocalDateTime.ofEpochSecond(endMsRow / 1000, 0, ZoneOffset.UTC)
            } else {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMsRow), zone)
                    .withSecond(0).withNano(0)
            }

            // Filter out events that don't actually occur on the requested dates (handles timezone shifts for all-day events)
            val logicalStart = startDt.toLocalDate()
            val logicalEnd = if (allDay) endDt.toLocalDate().minusDays(1) else endDt.toLocalDate()
            if (logicalStart.isAfter(endInclusive) || logicalEnd.isBefore(start)) {
                continue
            }

            list.add(
                CalendarEvent(
                    id = eventId,
                    calendarId = row.calendarId,
                    calendarDisplayName = row.calendarDisplayName ?: "",
                    calendarColor = row.calendarColor,
                    title = row.title ?: "(No title)",
                    description = row.description,
                    location = row.location,
                    start = startDt,
                    end = endDt,
                    allDay = allDay,
                    rrule = row.rrule,
                    timezone = row.timezone,
                    hasReminder = hasReminder,
                    reminders = reminders
                )
            )
        }
        return list
    }

    private fun queryRemindersIn(eventIds: List<Long>): Map<Long, List<Int>> {
        if (eventIds.isEmpty()) return emptyMap()
        val placeholders = eventIds.joinToString(",") { "?" }
        val args = eventIds.map { it.toString() }.toTypedArray()
        val out = HashMap<Long, MutableList<Int>>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
            args,
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val mins = c.getInt(1)
                out.getOrPut(id) { mutableListOf() }.add(mins)
            }
        }
        return out
    }

    private data class EventRow(
        val eventId: Long,
        val calendarId: Long,
        val title: String?,
        val description: String?,
        val location: String?,
        val beginMs: Long,
        val endMs: Long,
        val allDay: Boolean,
        val rrule: String?,
        val timezone: String?,
        val hasReminder: Boolean,
        val calendarDisplayName: String?,
        val calendarColor: Int
    )

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
        val reminders = mutableListOf<Int>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                reminders.add(c.getInt(0))
            }
        }

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
                        .withSecond(0).withNano(0)
                val endDt = if (allDay)
                    LocalDateTime.ofEpochSecond(endMs / 1000, 0, ZoneOffset.UTC)
                else
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMs), zone)
                        .withSecond(0).withNano(0)
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
                    reminders = reminders
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
        val reminders: List<Int> = emptyList(),
        val timezone: String = ZoneId.systemDefault().id,
        val rrule: String? = null
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
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                
                if (event.rrule != null) {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val days = java.time.temporal.ChronoUnit.DAYS.between(event.start.toLocalDate(), event.end.toLocalDate()) + 1
                    put(CalendarContract.Events.DURATION, "P${days}D")
                } else {
                    val endUtc = event.end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    put(CalendarContract.Events.DTEND, endUtc)
                }
            } else {
                val startMs = event.start.withSecond(0).withNano(0).atZone(zone).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone)
                
                if (event.rrule != null) {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val duration = java.time.Duration.between(event.start.withSecond(0).withNano(0), event.end.withSecond(0).withNano(0))
                    put(CalendarContract.Events.DURATION, "P${duration.seconds}S")
                } else {
                    val endMs = event.end.withSecond(0).withNano(0).atZone(zone).toInstant().toEpochMilli()
                    put(CalendarContract.Events.DTEND, endMs)
                }
            }
        }

        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)

        if (event.reminders.isNotEmpty() && !event.allDay) {
            event.reminders.forEach { mins ->
                val rv = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, mins)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
            }
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
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                
                if (event.rrule != null) {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val days = java.time.temporal.ChronoUnit.DAYS.between(event.start.toLocalDate(), event.end.toLocalDate()) + 1
                    put(CalendarContract.Events.DURATION, "P${days}D")
                    putNull(CalendarContract.Events.DTEND)
                } else {
                    val endUtc = event.end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    put(CalendarContract.Events.DTEND, endUtc)
                    putNull(CalendarContract.Events.RRULE)
                    putNull(CalendarContract.Events.DURATION)
                }
            } else {
                val startMs = event.start.withSecond(0).withNano(0).atZone(zone).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone)
                
                if (event.rrule != null) {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val duration = java.time.Duration.between(event.start.withSecond(0).withNano(0), event.end.withSecond(0).withNano(0))
                    put(CalendarContract.Events.DURATION, "P${duration.seconds}S")
                    putNull(CalendarContract.Events.DTEND)
                } else {
                    val endMs = event.end.withSecond(0).withNano(0).atZone(zone).toInstant().toEpochMilli()
                    put(CalendarContract.Events.DTEND, endMs)
                    putNull(CalendarContract.Events.RRULE)
                    putNull(CalendarContract.Events.DURATION)
                }
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
        if (event.reminders.isNotEmpty() && !event.allDay) {
            event.reminders.forEach { mins ->
                val rv = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, mins)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, rv)
            }
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
