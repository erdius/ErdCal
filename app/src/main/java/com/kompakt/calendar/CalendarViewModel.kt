package com.kompakt.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompakt.calendar.calendar.CalendarAccount
import com.kompakt.calendar.calendar.CalendarEvent
import com.kompakt.calendar.calendar.CalendarRepository
import com.kompakt.calendar.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: CalendarRepository =
        (app as MyApplication).calendarRepository

    private val prefs: UserPreferencesRepository =
        (app as MyApplication).userPreferencesRepository

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _agendaPage = MutableStateFlow(getPageForCurrentTime())
    val agendaPage: StateFlow<Int> = _agendaPage.asStateFlow()

    private val _eventNote = MutableStateFlow("")
    val eventNote: StateFlow<String> = _eventNote.asStateFlow()

    private val _hasPermission = MutableStateFlow(repo.hasReadPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /** Editing buffer for the AddEventScreen — null when creating a new event. */
    private val _editingEventId = MutableStateFlow<Long?>(null)
    val editingEventId: StateFlow<Long?> = _editingEventId.asStateFlow()

    /** Pre-fill state passed into AddEventScreen when editing. */
    private val _eventDraft = MutableStateFlow<EventDraft?>(null)
    val eventDraft: StateFlow<EventDraft?> = _eventDraft.asStateFlow()

    // Transient state for AddEventScreen to persist across navigation
    var draftTitle = MutableStateFlow("")
    var draftStartDate = MutableStateFlow(LocalDate.now())
    var draftEndDate = MutableStateFlow(LocalDate.now())
    var draftStartTime = MutableStateFlow(java.time.LocalTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(1))
    var draftEndTime = MutableStateFlow(java.time.LocalTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(2))
    var draftIsAllDay = MutableStateFlow(false)
    var draftLocation = MutableStateFlow("")
    var draftCalendarId = MutableStateFlow<Long?>(null)
    var draftReminders = MutableStateFlow<List<Int>>(listOf(5))
    var draftRrule = MutableStateFlow<String?>(null)
    var draftRruleUntil = MutableStateFlow<LocalDate?>(null)
    var draftRruleDays = MutableStateFlow<Set<Int>>(emptySet())

    fun updateDraftTitle(v: String) { draftTitle.value = v }
    fun updateDraftStartDate(v: LocalDate) { draftStartDate.value = v }
    fun updateDraftEndDate(v: LocalDate) { draftEndDate.value = v }
    fun updateDraftStartTime(v: java.time.LocalTime) { draftStartTime.value = v }
    fun updateDraftEndTime(v: java.time.LocalTime) { draftEndTime.value = v }
    fun updateDraftIsAllDay(v: Boolean) { draftIsAllDay.value = v }
    fun updateDraftLocation(v: String) { draftLocation.value = v }
    fun updateDraftCalendarId(v: Long?) { draftCalendarId.value = v }
    fun updateDraftReminders(v: List<Int>) { draftReminders.value = v }
    fun updateDraftRrule(v: String?) { draftRrule.value = v }
    fun updateDraftRruleUntil(v: LocalDate?) { draftRruleUntil.value = v }
    fun updateDraftRruleDays(v: Set<Int>) { draftRruleDays.value = v }

    val calendars: StateFlow<List<CalendarAccount>> =
        flow {
            emit(repo.getCalendars())
        }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Events for the currently shown month. Refreshes whenever month or DB changes. */
    private val _monthEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val monthEvents: StateFlow<List<CalendarEvent>> = _monthEvents.asStateFlow()

    /** Events on the currently selected day. */
    private val _dayEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val dayEvents: StateFlow<List<CalendarEvent>> = _dayEvents.asStateFlow()

    /** Upcoming events for the next 3 months. */
    private val _upcomingEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val upcomingEvents: StateFlow<List<CalendarEvent>> = _upcomingEvents.asStateFlow()

    fun refreshPermission() {
        val granted = repo.hasReadPermission()
        _hasPermission.value = granted
        if (granted) {
            // Force re-emit
            repo.notifyChanged()
            // Ensure observer is registered now that we have permission
            val app = getApplication<Application>() as MyApplication
            app.registerCalendarObserver()
            
            viewModelScope.launch(Dispatchers.IO) {
                // Schedule notifications for existing events
                NotificationScheduler.rescheduleAll(app)

                // refresh calendars list too
                val list = repo.getCalendars()
                _calendarsRefresh.value = list
            }
        }
    }

    private val _calendarsRefresh = MutableStateFlow<List<CalendarAccount>>(emptyList())
    val calendarsLive: StateFlow<List<CalendarAccount>> = _calendarsRefresh.asStateFlow()

    val defaultCalendarId: StateFlow<Long?> = prefs.defaultCalendarId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val defaultReminderMinutes: StateFlow<Int?> = prefs.defaultReminderMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    val showWeekNumbers: StateFlow<Boolean> = prefs.showWeekNumbers
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val startWeekOnMonday: StateFlow<Boolean> = prefs.startWeekOnMonday
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val onboardingCompleted: StateFlow<Boolean> = prefs.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val useAmericanDateFormat: StateFlow<Boolean> = prefs.useAmericanDateFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            _calendarsRefresh.value = repo.getCalendars()
        }

        viewModelScope.launch {
            combine(_currentMonth, _hasPermission, repo.changes.onStart { emit(Unit) }) { m, g, _ -> m to g }
                .collectLatest { (month, granted) ->
                    _monthEvents.value = emptyList()
                    if (granted) {
                        val events = withContext(Dispatchers.IO) { repo.getEventsForMonth(month) }
                        _monthEvents.value = events
                    }
                }
        }

        viewModelScope.launch {
            combine(_selectedDate, _hasPermission, repo.changes.onStart { emit(Unit) }) { d, g, _ -> d to g }
                .collectLatest { (date, granted) ->
                    _dayEvents.value = emptyList()
                    if (granted) {
                        val events = withContext(Dispatchers.IO) { repo.getEventsForDate(date) }
                        _dayEvents.value = events
                    }
                }
        }

        viewModelScope.launch {
            combine(_hasPermission, repo.changes.onStart { emit(Unit) }) { g, _ -> g }
                .collectLatest { granted ->
                    _upcomingEvents.value = emptyList()
                    if (granted) {
                        val events = withContext(Dispatchers.IO) {
                            val today = LocalDate.now()
                            repo.getEventsBetween(today, today.plusMonths(3))
                        }
                        _upcomingEvents.value = events
                    }
                }
        }
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        if (date == LocalDate.now()) {
            _agendaPage.value = getPageForCurrentTime()
        }
    }

    fun nextMonth() { _currentMonth.value = _currentMonth.value.plusMonths(1) }
    fun previousMonth() { _currentMonth.value = _currentMonth.value.minusMonths(1) }
    fun nextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
        _currentMonth.value = YearMonth.from(_selectedDate.value)
    }
    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
        _currentMonth.value = YearMonth.from(_selectedDate.value)
    }
    fun setAgendaPage(page: Int) { if (page in 0..2) _agendaPage.value = page }
    fun updateEventNote(note: String) { _eventNote.value = note }
    fun goToToday() {
        val today = LocalDate.now()
        _currentMonth.value = YearMonth.from(today)
        _selectedDate.value = today
        _agendaPage.value = getPageForCurrentTime()
    }

    fun beginNewEvent() {
        _editingEventId.value = null
        _eventDraft.value = null
        _eventNote.value = ""

        draftTitle.value = ""
        val selectedDay = _selectedDate.value
        draftStartDate.value = selectedDay
        draftEndDate.value = selectedDay
        val time = java.time.LocalTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(1)
        draftStartTime.value = time
        draftEndTime.value = time.plusHours(1)
        draftIsAllDay.value = false
        draftLocation.value = ""
        draftCalendarId.value = null
        draftReminders.value = listOf(5)
        draftRrule.value = null
        draftRruleUntil.value = null
        draftRruleDays.value = emptySet()
    }

    fun beginEditEvent(event: CalendarEvent) {
        _editingEventId.value = event.id
        _eventNote.value = event.description ?: ""
        _selectedDate.value = event.start.toLocalDate()

        draftTitle.value = event.title
        draftStartDate.value = event.start.toLocalDate()
        draftEndDate.value = if (event.allDay) event.end.toLocalDate().minusDays(1) else event.end.toLocalDate()
        draftStartTime.value = event.start.toLocalTime()
        draftEndTime.value = event.end.toLocalTime()
        draftIsAllDay.value = event.allDay
        draftLocation.value = event.location ?: ""
        draftCalendarId.value = event.calendarId
        draftReminders.value = event.reminders
        
        // Parse RRULE for UNTIL and BYDAY
        var baseRrule = event.rrule
        var untilDate: LocalDate? = null
        var selectedDays = emptySet<Int>()
        
        if (baseRrule != null) {
            val parts = baseRrule.split(";")
            
            val untilPart = parts.find { it.startsWith("UNTIL=") }
            if (untilPart != null) {
                val dateStr = untilPart.substring(6)
                try {
                    // Handle both YYYYMMDD and YYYYMMDDTHHMMSSZ
                    val cleanStr = if (dateStr.contains("T")) dateStr.split("T")[0] else dateStr
                    if (cleanStr.length >= 8) {
                        val y = cleanStr.substring(0, 4).toInt()
                        val m = cleanStr.substring(4, 6).toInt()
                        val d = cleanStr.substring(6, 8).toInt()
                        untilDate = LocalDate.of(y, m, d)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CalendarViewModel", "Failed to parse RRULE UNTIL: $dateStr", e)
                }
            }
            
            val byDayPart = parts.find { it.startsWith("BYDAY=") }
            if (byDayPart != null) {
                val daysStr = byDayPart.substring(6)
                val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
                // Filter out prefixes like 1, 2, -1 which are used for monthly (e.g. 1MO for first Monday)
                selectedDays = daysStr.split(",").mapNotNull { 
                    val dayCode = it.trim().takeLast(2).uppercase()
                    dayMap[dayCode] 
                }.toSet()
            }
            
            baseRrule = parts.filter { !it.startsWith("UNTIL=") && !it.startsWith("BYDAY=") }.joinToString(";")
        }
        
        draftRrule.value = baseRrule
        draftRruleUntil.value = untilDate
        draftRruleDays.value = selectedDays
    }

    suspend fun saveEvent(draft: EventDraft, reminders: List<Int>): Boolean {
        val cal = draft.calendarId ?: return false
        
        var finalRrule = draft.rrule
        if (finalRrule != null) {
            if (draft.rruleDays.isNotEmpty() && finalRrule.contains("WEEKLY")) {
                val dayMap = mapOf(1 to "MO", 2 to "TU", 3 to "WE", 4 to "TH", 5 to "FR", 6 to "SA", 7 to "SU")
                val byDay = draft.rruleDays.sorted().mapNotNull { dayMap[it] }.joinToString(",")
                finalRrule = "$finalRrule;BYDAY=$byDay"
            }
            
            if (draft.rruleUntil != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T235959Z'")
                val untilStr = draft.rruleUntil.format(formatter)
                finalRrule = "$finalRrule;UNTIL=$untilStr"
            }
        }

        val ne = CalendarRepository.NewEvent(
            calendarId = cal,
            title = draft.title.ifBlank { "(No title)" },
            description = draft.description.ifBlank { null },
            location = draft.location.ifBlank { null },
            start = draft.startDate.atTime(draft.startTime),
            end = draft.endDate.atTime(draft.endTime),
            allDay = draft.allDay,
            reminders = reminders,
            rrule = finalRrule
        )
        val editId = _editingEventId.value
        return if (editId != null) {
            val ok = repo.updateEvent(editId, ne)
            if (ok) {
                NotificationScheduler.cancelEventNotifications(getApplication(), editId)
                val startMs = ne.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs = ne.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                NotificationScheduler.scheduleEventNotifications(
                    getApplication(), editId, ne.title, startMs, endMs, reminders
                )
            }
            ok
        } else {
            val id = repo.insertEvent(ne)
            if (id != null) {
                val startMs = ne.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs = ne.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                NotificationScheduler.scheduleEventNotifications(
                    getApplication(), id, ne.title, startMs, endMs, reminders
                )
            }
            id != null
        }
    }

    suspend fun deleteCurrentEvent(): Boolean {
        val id = _editingEventId.value ?: return false
        NotificationScheduler.cancelEventNotifications(getApplication(), id)
        return repo.deleteEvent(id)
    }

    suspend fun deleteEventById(id: Long): Boolean {
        NotificationScheduler.cancelEventNotifications(getApplication(), id)
        return repo.deleteEvent(id)
    }

    suspend fun loadEventById(id: Long): CalendarEvent? = repo.getEventById(id)

    suspend fun search(query: String): List<CalendarEvent> = repo.searchEvents(query)

    suspend fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        repo.setCalendarVisibility(calendarId, visible)
        _calendarsRefresh.value = repo.getCalendars()
    }

    suspend fun setDefaultCalendar(calendarId: Long) {
        prefs.saveDefaultCalendarId(calendarId)
    }

    suspend fun setDefaultReminderMinutes(minutes: Int?) {
        prefs.saveDefaultReminderMinutes(minutes ?: -1) // use -1 for "No reminder" internally if needed, or just allow null in repo
    }

    suspend fun setShowWeekNumbers(show: Boolean) {
        prefs.saveShowWeekNumbers(show)
    }

    suspend fun setStartWeekOnMonday(monday: Boolean) {
        prefs.saveStartWeekOnMonday(monday)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        prefs.saveOnboardingCompleted(completed)
    }

    suspend fun setUseAmericanDateFormat(use: Boolean) {
        prefs.saveUseAmericanDateFormat(use)
    }

    private fun getPageForCurrentTime(): Int {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 8 -> 0
            hour < 19 -> 1
            else -> 2
        }
    }
}

data class EventDraft(
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val startTime: java.time.LocalTime = java.time.LocalTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(1),
    val endTime: java.time.LocalTime = java.time.LocalTime.now().withSecond(0).withNano(0).withMinute(0).plusHours(2),
    val allDay: Boolean = false,
    val location: String = "",
    val calendarId: Long? = null,
    val description: String = "",
    val reminders: List<Int> = emptyList(),
    val rrule: String? = null,
    val rruleUntil: LocalDate? = null,
    val rruleDays: Set<Int> = emptySet()
)
