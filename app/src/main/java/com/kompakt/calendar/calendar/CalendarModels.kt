package com.kompakt.calendar.calendar

import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String?,
    val color: Int,
    val isPrimary: Boolean,
    val isVisible: Boolean,
    val isWritable: Boolean
) {
    val isDavx5: Boolean
        get() = accountType.equals("bitfire.at.davdroid", ignoreCase = true) ||
                accountType.contains("davx", ignoreCase = true) ||
                accountType.contains("davdroid", ignoreCase = true)
}

data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val calendarDisplayName: String,
    val calendarColor: Int,
    val title: String,
    val description: String?,
    val location: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val rrule: String?,
    val timezone: String?,
    val hasReminder: Boolean,
    val reminderMinutes: Int? = null
) {
    fun occursOn(date: LocalDate): Boolean {
        val s = start.toLocalDate()
        val e = end.toLocalDate()
        return !date.isBefore(s) && !date.isAfter(if (allDay) e.minusDays(1) else e)
    }
}
