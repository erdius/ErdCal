package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarAccount
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.eInkVerticalScroll
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel(),
    useToday: Boolean = false
) {
    val title by viewModel.draftTitle.collectAsState()
    val startDate by viewModel.draftStartDate.collectAsState()
    val endDate by viewModel.draftEndDate.collectAsState()
    val startTime by viewModel.draftStartTime.collectAsState()
    val endTime by viewModel.draftEndTime.collectAsState()
    val isAllDay by viewModel.draftIsAllDay.collectAsState()
    val selectedCalendarId by viewModel.draftCalendarId.collectAsState()
    val selectedReminders by viewModel.draftReminders.collectAsState()
    val rrule by viewModel.draftRrule.collectAsState()
    val useAmericanDateFormat by viewModel.useAmericanDateFormat.collectAsState()
    val rruleUntil by viewModel.draftRruleUntil.collectAsState()
    val rruleDays by viewModel.draftRruleDays.collectAsState()
    val location by viewModel.draftLocation.collectAsState()

    val eventNote by viewModel.eventNote.collectAsState()
    val editingId by viewModel.editingEventId.collectAsState()
    val calendars by viewModel.calendarsLive.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
    
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEdit = editingId != null

    // Ensure calendar ID is initialized if null
    LaunchedEffect(calendars, defaultCalendarId, selectedCalendarId) {
        if (selectedCalendarId == null) {
            val writableCalendars = calendars.filter { it.isWritable }
            val id = defaultCalendarId
                ?: writableCalendars.firstOrNull { it.isPrimary }?.id
                ?: writableCalendars.firstOrNull()?.id
            if (id != null) viewModel.updateDraftCalendarId(id)
        }
    }

    var showReminderPicker by remember { mutableStateOf(false) }
    var showCustomReminderPicker by remember { mutableStateOf(false) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showRecurrencePicker by remember { mutableStateOf(false) }

    val reminderOptions = remember(isAllDay) {
        if (isAllDay) {
            listOf(
                "On the day (8:00 am)" to -480,
                "On the day (9:00 am)" to -540,
                "Day before (6:00 pm)" to 360,
                "Day before (8:00 pm)" to 240,
                "1 day before" to 1440,
                "2 days before" to 2880,
                "1 week before" to 10080
            )
        } else {
            listOf(
                "5 minutes before" to 5,
                "10 minutes before" to 10,
                "15 minutes before" to 15,
                "30 minutes before" to 30,
                "1 hour before" to 60,
                "1 day before" to 1440,
                "1 week before" to 10080
            )
        }
    }

    val recurrenceOptions = listOf(
        "Does not repeat" to null,
        "Daily" to "FREQ=DAILY",
        "Weekly" to "FREQ=WEEKLY",
        "Bi-weekly" to "FREQ=WEEKLY;INTERVAL=2",
        "Monthly" to "FREQ=MONTHLY",
        "Yearly" to "FREQ=YEARLY"
    )

    val writableCalendars = remember(calendars) { calendars.filter { it.isWritable } }
    val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }

    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color.White,
                    modifier = Modifier.statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(28.dp))
                        }

                        TextField(
                            value = title,
                            onValueChange = { viewModel.updateDraftTitle(it) },
                            placeholder = { TextMMD("Add Title", fontSize = 24.sp, color = Color.LightGray) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        )

                        ButtonMMD(
                            onClick = {
                                if (selectedCalendarId == null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "No writable calendar available. Add an account in DAVx5 or Google first.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    return@ButtonMMD
                                }
                                scope.launch {
                                    val ok = viewModel.saveEvent(
                                        EventDraft(
                                            title = title,
                                            startDate = startDate,
                                            endDate = endDate,
                                            startTime = startTime,
                                            endTime = endTime,
                                            allDay = isAllDay,
                                            location = location,
                                            calendarId = selectedCalendarId,
                                            description = eventNote,
                                            rrule = rrule,
                                            rruleUntil = rruleUntil,
                                            rruleDays = rruleDays
                                        ),
                                        reminders = selectedReminders
                                    )
                                    if (ok) {
                                        navController.popBackStack()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Could not save event.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            TextMMD(if (isEdit) "Save" else "Create", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .eInkVerticalScroll(listState, scope, isScrollable),
                    userScrollEnabled = false
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.Black)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }

                    item {
                        DateTimeRow(
                            label = "FROM",
                            date = startDate,
                            time = startTime,
                            isAllDay = isAllDay,
                            useAmericanDateFormat = useAmericanDateFormat,
                            onDateChange = {
                                viewModel.updateDraftStartDate(it)
                                if (endDate.isBefore(it)) viewModel.updateDraftEndDate(it)
                            },
                            onTimeChange = { viewModel.updateDraftStartTime(it) }
                        )
                    }

                    item {
                        DateTimeRow(
                            label = "TO",
                            date = endDate,
                            time = endTime,
                            isAllDay = isAllDay,
                            useAmericanDateFormat = useAmericanDateFormat,
                            onDateChange = {
                                viewModel.updateDraftEndDate(it)
                                if (it.isBefore(startDate)) viewModel.updateDraftStartDate(it)
                            },
                            onTimeChange = { viewModel.updateDraftEndTime(it) }
                        )
                    }

                    item {
                        OptionRow(
                            icon = Icons.Outlined.Today,
                            title = "All-day event",
                            checked = isAllDay,
                            onCheckedChange = { viewModel.updateDraftIsAllDay(it) }
                        )
                    }

                    item {
                        HorizontalDividerMMD(
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Black
                        )
                    }

                    item {
                        OptionRow(
                            icon = Icons.Default.CalendarMonth,
                            title = recurrenceOptions.find { it.second == rrule }?.first ?: "Does not repeat",
                            hasChevron = true,
                            onClick = { showRecurrencePicker = true }
                        )
                    }

                    item {
                        HorizontalDividerMMD(
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Black
                        )
                    }

                    item {
                        val reminderText = if (selectedReminders.isEmpty()) {
                            "No reminder"
                        } else {
                            selectedReminders.joinToString(", ") { mins ->
                                reminderOptions.find { it.second == mins }?.first ?: formatMinutes(mins)
                            }
                        }
                        OptionRow(
                            icon = Icons.Outlined.NotificationsNone,
                            title = reminderText,
                            hasChevron = true,
                            onClick = { showReminderPicker = true },
                            enabled = !isAllDay
                        )
                    }

                    item {
                        HorizontalDividerMMD(
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Black
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            CompactOptionItem(
                                icon = Icons.Outlined.LocationOn,
                                title = if (location.isEmpty()) "Add location" else {
                                    if (location.length > 10) "${location.take(10)}..." else location
                                },
                                onClick = { navController.navigate("location") },
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Color.Black)
                                    .align(Alignment.CenterVertically)
                            )
                            CompactOptionItem(
                                icon = Icons.Outlined.Description,
                                title = if (eventNote.isEmpty()) {
                                    "Add notes"
                                } else {
                                    val displayNote = eventNote.replace("\n", " ")
                                    if (displayNote.length > 10) "${displayNote.take(10)}..." else displayNote
                                },
                                onClick = { navController.navigate("notes") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        HorizontalDividerMMD(
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Black
                        )
                    }

                    item {
                        OptionRow(
                            icon = Icons.Default.CalendarMonth,
                            title = selectedCalendar?.let {
                                "${it.displayName}${if (it.isDavx5) " (DAVx5)" else ""}"
                            } ?: "Choose calendar",
                            hasChevron = true,
                            onClick = { showCalendarPicker = true }
                        )
                    }
                }
                if (isScrollable) {
                    EInkScrollbar(state = listState, scope = scope)
                }
            }
        }

        if (showCalendarPicker) {
            CalendarPickerOverlay(
                calendars = writableCalendars,
                selectedId = selectedCalendarId,
                onPick = {
                    viewModel.updateDraftCalendarId(it)
                    showCalendarPicker = false
                },
                onDismiss = { showCalendarPicker = false }
            )
        }

        if (showReminderPicker) {
            ReminderPickerOverlay(
                options = reminderOptions,
                selectedReminders = selectedReminders,
                onPick = {
                    viewModel.updateDraftReminders(it)
                },
                onCustomClick = {
                    showReminderPicker = false
                    showCustomReminderPicker = true
                },
                onDismiss = { showReminderPicker = false }
            )
        }

        if (showCustomReminderPicker) {
            CustomReminderPickerOverlay(
                onPick = { minutes ->
                    viewModel.updateDraftReminders(selectedReminders + minutes)
                    showCustomReminderPicker = false
                },
                onDismiss = { showCustomReminderPicker = false }
            )
        }

        if (showRecurrencePicker) {
            RecurrencePickerOverlay(
                options = recurrenceOptions,
                selectedRrule = rrule,
                selectedUntil = rruleUntil,
                selectedDays = viewModel.draftRruleDays.collectAsState().value,
                startDate = startDate,
                useAmericanDateFormat = useAmericanDateFormat,
                onPick = { rule, until, days ->
                    viewModel.updateDraftRrule(rule)
                    viewModel.updateDraftRruleUntil(until)
                    viewModel.updateDraftRruleDays(days)
                    showRecurrencePicker = false
                },
                onDismiss = { showRecurrencePicker = false }
            )
        }
    }
}

@Composable
private fun RecurrencePickerOverlay(
    options: List<Pair<String, String?>>,
    selectedRrule: String?,
    selectedUntil: LocalDate?,
    selectedDays: Set<Int>,
    startDate: LocalDate,
    useAmericanDateFormat: Boolean,
    onPick: (String?, LocalDate?, Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    var currentRrule by remember { mutableStateOf(selectedRrule) }
    var currentUntil by remember { mutableStateOf(selectedUntil) }
    var currentDays by remember { mutableStateOf(selectedDays) }

    // When switching to weekly, if no days are selected, default to the event's start day
    LaunchedEffect(currentRrule) {
        if (currentRrule?.contains("WEEKLY") == true && currentDays.isEmpty()) {
            currentDays = setOf(startDate.dayOfWeek.value)
        }
    }

    val daysOfWeek = listOf(
        "Mo" to 1, "Di" to 2, "Mi" to 3, "Do" to 4, "Fr" to 5, "Sa" to 6, "So" to 7
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                TextMMD(
                    "Set Recurrence",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                ButtonMMD(
                    onClick = { onPick(currentRrule, currentUntil, currentDays) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    TextMMD("Done", fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .eInkVerticalScroll(listState, scope, isScrollable)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    userScrollEnabled = false
                ) {
                    items(options) { (label, rule) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentRrule = rule }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextMMD(
                                label,
                                fontSize = 16.sp,
                                fontWeight = if (currentRrule == rule) FontWeight.Bold else FontWeight.Normal
                            )
                            RadioButtonMMD(
                                selected = currentRrule == rule,
                                onClick = { currentRrule = rule }
                            )
                        }
                        HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                    }

                    if (currentRrule != null) {
                        if (currentRrule?.contains("WEEKLY") == true) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextMMD("Repeat on", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    daysOfWeek.forEach { (name, dayNum) ->
                                        val isSelected = currentDays.contains(dayNum)
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    if (isSelected) Color.Black else Color.White,
                                                    CircleShape
                                                )
                                                .border(1.dp, Color.Black, CircleShape)
                                                .clickable {
                                                    currentDays = if (isSelected) {
                                                        if (currentDays.size > 1) currentDays - dayNum else currentDays
                                                    } else {
                                                        currentDays + dayNum
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            TextMMD(
                                                text = name,
                                                color = if (isSelected) Color.White else Color.Black,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextMMD("Repeat for", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val unitName = when {
                                currentRrule?.contains("DAILY") == true -> "day"
                                currentRrule?.contains("WEEKLY") == true -> "week"
                                currentRrule?.contains("MONTHLY") == true -> "month"
                                currentRrule?.contains("YEARLY") == true -> "year"
                                else -> "unit"
                            }
                            
                            val duration = if (currentUntil == null) 0 else {
                                when (unitName) {
                                    "day" -> java.time.temporal.ChronoUnit.DAYS.between(startDate, currentUntil).toInt()
                                    "week" -> java.time.temporal.ChronoUnit.WEEKS.between(startDate, currentUntil).toInt()
                                    "month" -> java.time.temporal.ChronoUnit.MONTHS.between(startDate, currentUntil).toInt()
                                    "year" -> java.time.temporal.ChronoUnit.YEARS.between(startDate, currentUntil).toInt()
                                    else -> 0
                                }
                            }.coerceAtLeast(0)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    TextMMD(
                                        text = if (duration == 0) "Forever" else "$duration ${unitName}${if (duration > 1) "s" else ""}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (currentUntil != null) {
                                        val pattern = if (useAmericanDateFormat) "EEE, MMM d yyyy" else "EEE, d MMM yyyy"
                                        TextMMD(
                                            text = "Until ${currentUntil?.format(DateTimeFormatter.ofPattern(pattern, Locale.US))}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (duration > 0) {
                                            val newDuration = duration - 1
                                            currentUntil = if (newDuration == 0) null else {
                                                when (unitName) {
                                                    "day" -> startDate.plusDays(newDuration.toLong())
                                                    "week" -> startDate.plusWeeks(newDuration.toLong())
                                                    "month" -> startDate.plusMonths(newDuration.toLong())
                                                    "year" -> startDate.plusYears(newDuration.toLong())
                                                    else -> currentUntil
                                                }
                                            }
                                        }
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Decrease duration")
                                    }
                                    
                                    IconButton(onClick = {
                                        val newDuration = duration + 1
                                        currentUntil = when (unitName) {
                                            "day" -> startDate.plusDays(newDuration.toLong())
                                            "week" -> startDate.plusWeeks(newDuration.toLong())
                                            "month" -> startDate.plusMonths(newDuration.toLong())
                                            "year" -> startDate.plusYears(newDuration.toLong())
                                            else -> currentUntil
                                        }
                                    }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Increase duration")
                                    }
                                }
                            }
                        }
                    }
                }
                if (isScrollable) {
                    EInkScrollbar(state = listState, scope = scope)
                }
            }
        }
    }
}

@Composable
private fun ReminderPickerOverlay(
    options: List<Pair<String, Int?>>,
    selectedReminders: List<Int>,
    onPick: (List<Int>) -> Unit,
    onCustomClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                TextMMD(
                    "Set Reminders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                ButtonMMD(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    TextMMD("Done", fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            Row(modifier = Modifier.fillMaxSize()) {
                val predefinedOptions = remember(options) { options.filter { it.second != null } }
                val customSelections = remember(options, selectedReminders) {
                    selectedReminders.filter { mins -> options.none { it.second == mins } }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .eInkVerticalScroll(listState, scope, isScrollable)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    userScrollEnabled = false
                ) {
                    items(predefinedOptions) { (label, minutes) ->
                        val isSelected = selectedReminders.contains(minutes!!)
                        ReminderToggleRow(
                            label = label,
                            isSelected = isSelected,
                            onToggle = {
                                if (isSelected) onPick(selectedReminders - minutes)
                                else onPick(selectedReminders + minutes)
                            }
                        )
                        HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                    }

                    if (customSelections.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextMMD("Custom Reminders", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                        items(customSelections) { minutes ->
                            ReminderToggleRow(
                                label = formatMinutes(minutes),
                                isSelected = true,
                                onToggle = { onPick(selectedReminders - minutes) }
                            )
                            HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        ButtonMMD(
                            onClick = onCustomClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            TextMMD("Add Custom Reminder", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                if (isScrollable) {
                    EInkScrollbar(state = listState, scope = scope)
                }
            }
        }
    }
}

@Composable
private fun ReminderToggleRow(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextMMD(
            label,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        CheckboxMMD(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CustomReminderPickerOverlay(
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf(TextFieldValue("010", TextRange(0))) }
    var unit by remember { mutableStateOf("minutes") }
    val units = listOf("minutes", "hours", "days", "weeks")
    
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                TextMMD(
                    "Custom Reminder",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                ButtonMMD(
                    onClick = {
                        val amount = amountStr.text.toIntOrNull() ?: 0
                        val minutes = when (unit) {
                            "minutes" -> amount
                            "hours" -> amount * 60
                            "days" -> amount * 1440
                            "weeks" -> amount * 10080
                            else -> amount
                        }
                        onPick(minutes)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    TextMMD("Add", fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextMMD("Remind me", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Numeric Input (3 digits)
                    Box(modifier = Modifier.clickable { 
                        amountStr = amountStr.copy(selection = TextRange(0))
                        focusRequester.requestFocus() 
                    }) {
                        BasicTextField(
                            value = amountStr,
                            onValueChange = { newValue ->
                                val oldStr = amountStr.text
                                val newStr = newValue.text
                                val newCursor = newValue.selection.start

                                if (newStr.length > oldStr.length) {
                                    val addedDigit = newStr.getOrNull(newCursor - 1)
                                    if (addedDigit != null && addedDigit.isDigit()) {
                                        val pos = newCursor - 1
                                        if (pos < 3) {
                                            val updatedText = oldStr.substring(0, pos) + addedDigit + oldStr.substring(pos + 1)
                                            amountStr = TextFieldValue(updatedText, TextRange((pos + 1).coerceAtMost(3)))
                                            if (pos == 2) focusManager.clearFocus()
                                        }
                                    }
                                } else if (newStr.length < oldStr.length) {
                                    amountStr = newValue.copy(text = oldStr)
                                } else {
                                    amountStr = newValue
                                }
                            },
                            modifier = Modifier
                                .size(1.dp)
                                .alpha(0f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused }
                                .onKeyEvent {
                                    if (it.key == Key.Backspace) {
                                        val pos = amountStr.selection.start
                                        if (pos > 0) {
                                            amountStr = amountStr.copy(selection = TextRange(pos - 1))
                                        }
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            cursorBrush = SolidColor(Color.Transparent)
                        )

                        Row {
                            val cursor = if (isFocused) amountStr.selection.start else -1
                            DigitBox(amountStr.text.getOrNull(0)?.toString() ?: "0", cursor == 0)
                            DigitBox(amountStr.text.getOrNull(1)?.toString() ?: "0", cursor == 1)
                            DigitBox(amountStr.text.getOrNull(2)?.toString() ?: "0", cursor == 2)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Unit Selection
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .clickable { 
                                val nextIndex = (units.indexOf(unit) + 1) % units.size
                                unit = units[nextIndex]
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        TextMMD(unit, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextMMD("before the event", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}


@Composable
private fun CalendarPickerOverlay(
    calendars: List<CalendarAccount>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                TextMMD(
                    "Select Calendar",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .eInkVerticalScroll(listState, scope, isScrollable)
                        .padding(16.dp),
                    userScrollEnabled = false
                ) {
                    if (calendars.isEmpty()) {
                        item {
                            TextMMD("No writable calendars found. Sign in via DAVx5 or Google to add one.")
                        }
                    } else {
                        items(calendars) { cal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(cal.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    TextMMD(
                                        cal.displayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    TextMMD(
                                        "${cal.accountName}${if (cal.isDavx5) " · DAVx5" else ""}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                RadioButtonMMD(
                                    selected = cal.id == selectedId,
                                    onClick = { onPick(cal.id) }
                                )
                            }
                            HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                        }
                    }
                }
                if (isScrollable) {
                    EInkScrollbar(state = listState, scope = scope)
                }
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    return when {
        minutes % 10080 == 0 -> "${minutes / 10080} week${if (minutes / 10080 > 1) "s" else ""} before"
        minutes % 1440 == 0 -> "${minutes / 1440} day${if (minutes / 1440 > 1) "s" else ""} before"
        minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""} before"
        else -> "$minutes minutes before"
    }
}


@Composable
fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    isAllDay: Boolean,
    useAmericanDateFormat: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    val dateValue = remember(date, useAmericanDateFormat) {
        val d = String.format(Locale.US, "%02d", date.dayOfMonth)
        val m = String.format(Locale.US, "%02d", date.monthValue)
        val y = String.format(Locale.US, "%02d", date.year % 100)
        if (useAmericanDateFormat) m + d + y else d + m + y
    }
    var localDateStr by remember(dateValue) { mutableStateOf(TextFieldValue(dateValue, TextRange(0))) }

    val timeValue = remember(time) {
        val h = String.format(Locale.US, "%02d", time.hour)
        val m = String.format(Locale.US, "%02d", time.minute)
        h + m
    }
    var localTimeStr by remember(timeValue) { mutableStateOf(TextFieldValue(timeValue, TextRange(0))) }

    val dateFocusRequester = remember { FocusRequester() }
    val timeFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isDateFocused by remember { mutableStateOf(false) }
    var isTimeFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        TextMMD(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DATE SECTION
            Box(
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    value = localDateStr,
                    onValueChange = { newValue ->
                        val oldStr = localDateStr.text
                        val newStr = newValue.text
                        val newCursor = newValue.selection.start

                        if (newStr.length > oldStr.length) {
                            val addedDigit = newStr.getOrNull(newCursor - 1)
                            if (addedDigit != null && addedDigit.isDigit()) {
                                val pos = newCursor - 1
                                if (pos < 6) {
                                    val isValid = when (pos) {
                                        0 -> if (useAmericanDateFormat) addedDigit <= '1' else addedDigit <= '3'
                                        2 -> if (useAmericanDateFormat) addedDigit <= '3' else addedDigit <= '1'
                                        else -> true
                                    }
                                    if (isValid) {
                                        val updatedText = oldStr.substring(0, pos) + addedDigit + oldStr.substring(pos + 1)
                                        if (pos == 5) {
                                            localDateStr = TextFieldValue(updatedText, TextRange(6))
                                            try {
                                                val p1 = updatedText.substring(0, 2).toInt()
                                                val p2 = updatedText.substring(2, 4).toInt()
                                                val p3 = 2000 + updatedText.substring(4, 6).toInt()
                                                val newDate = if (useAmericanDateFormat) {
                                                    LocalDate.of(p3, p1.coerceIn(1, 12), p2.coerceIn(1, 31))
                                                } else {
                                                    LocalDate.of(p3, p2.coerceIn(1, 12), p1.coerceIn(1, 31))
                                                }
                                                onDateChange(newDate)
                                            } catch (e: Exception) {}
                                            if (!isAllDay) {
                                                localTimeStr = localTimeStr.copy(selection = TextRange(0))
                                                timeFocusRequester.requestFocus()
                                            } else {
                                                focusManager.clearFocus()
                                            }
                                        } else {
                                            localDateStr = TextFieldValue(updatedText, TextRange(pos + 1))
                                        }
                                    }
                                }
                            }
                        } else if (newStr.length < oldStr.length) {
                            localDateStr = newValue.copy(text = oldStr)
                        } else {
                            localDateStr = newValue
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(dateFocusRequester)
                        .onFocusChanged { isDateFocused = it.isFocused }
                        .onKeyEvent {
                            if (it.key == Key.Backspace) {
                                val pos = localDateStr.selection.start
                                if (pos > 0) {
                                    localDateStr = localDateStr.copy(selection = TextRange(pos - 1))
                                }
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (isAllDay) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (!isAllDay) timeFocusRequester.requestFocus() }, onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(Color.Transparent)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val currentText = localDateStr.text.padEnd(6, ' ')
                    val cursor = if (isDateFocused) localDateStr.selection.start else -1

                    if (useAmericanDateFormat) {
                        DateSegment(currentText.substring(0, 2), cursor, 0, onClick = {
                            localDateStr = localDateStr.copy(selection = TextRange(0))
                            dateFocusRequester.requestFocus()
                        })
                        TextMMD("/", modifier = Modifier.padding(horizontal = 1.dp))
                        DateSegment(currentText.substring(2, 4), cursor, 2, onClick = {
                            localDateStr = localDateStr.copy(selection = TextRange(2))
                            dateFocusRequester.requestFocus()
                        })
                    } else {
                        DateSegment(currentText.substring(0, 2), cursor, 0, onClick = {
                            localDateStr = localDateStr.copy(selection = TextRange(0))
                            dateFocusRequester.requestFocus()
                        })
                        TextMMD("/", modifier = Modifier.padding(horizontal = 1.dp))
                        DateSegment(currentText.substring(2, 4), cursor, 2, onClick = {
                            localDateStr = localDateStr.copy(selection = TextRange(2))
                            dateFocusRequester.requestFocus()
                        })
                    }
                    TextMMD("/", modifier = Modifier.padding(horizontal = 1.dp))
                    DateSegment(currentText.substring(4, 6), cursor, 4, onClick = {
                        localDateStr = localDateStr.copy(selection = TextRange(4))
                        dateFocusRequester.requestFocus()
                    })
                }
            }

            if (!isAllDay) {
                Spacer(modifier = Modifier.width(8.dp))
                // TIME SECTION
                Box {
                    BasicTextField(
                        value = localTimeStr,
                        onValueChange = { newValue ->
                            val oldStr = localTimeStr.text
                            val newStr = newValue.text
                            val newCursor = newValue.selection.start

                            if (newStr.length > oldStr.length) {
                                val addedDigit = newStr.getOrNull(newCursor - 1)
                                if (addedDigit != null && addedDigit.isDigit()) {
                                    val pos = newCursor - 1
                                    if (pos < 4) {
                                        val isValid = when (pos) {
                                            0 -> if (is24Hour) addedDigit <= '2' else addedDigit <= '1'
                                            2 -> addedDigit <= '5'
                                            else -> true
                                        }
                                        if (isValid) {
                                            val updatedText = oldStr.substring(0, pos) + addedDigit + oldStr.substring(pos + 1)
                                            if (pos == 3) {
                                                localTimeStr = TextFieldValue(updatedText, TextRange(4))
                                                try {
                                                    val h24 = updatedText.substring(0, 2).toInt().coerceIn(0, 23)
                                                    val m = updatedText.substring(2, 4).toInt().coerceIn(0, 59)
                                                    onTimeChange(LocalTime.of(h24, m))
                                                } catch (e: Exception) {}
                                                focusManager.clearFocus()
                                            } else {
                                                localTimeStr = TextFieldValue(updatedText, TextRange(pos + 1))
                                            }
                                        }
                                    }
                                }
                            } else if (newStr.length < oldStr.length) {
                                localTimeStr = newValue.copy(text = oldStr)
                            } else {
                                localTimeStr = newValue
                            }
                        },
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                            .focusRequester(timeFocusRequester)
                            .onFocusChanged { isTimeFocused = it.isFocused }
                            .onKeyEvent {
                                if (it.key == Key.Backspace) {
                                    val pos = localTimeStr.selection.start
                                    if (pos > 0) {
                                        localTimeStr = localTimeStr.copy(selection = TextRange(pos - 1))
                                    } else {
                                        localDateStr = localDateStr.copy(selection = TextRange(5))
                                        dateFocusRequester.requestFocus()
                                    }
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        cursorBrush = SolidColor(Color.Transparent)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayHH = if (is24Hour) {
                            localTimeStr.text.substring(0, 2)
                        } else {
                            val h24 = localTimeStr.text.substring(0, 2).toIntOrNull() ?: time.hour
                            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
                            String.format(Locale.US, "%02d", h12)
                        }
                        val currentText = displayHH + localTimeStr.text.substring(2, 4)
                        val cursor = if (isTimeFocused) localTimeStr.selection.start else -1
                        
                        DateSegment(currentText.substring(0, 2), cursor, 0, onClick = {
                            localTimeStr = localTimeStr.copy(selection = TextRange(0))
                            timeFocusRequester.requestFocus()
                        })
                        TextMMD(":", modifier = Modifier.padding(horizontal = 1.dp))
                        DateSegment(currentText.substring(2, 4), cursor, 2, onClick = {
                            localTimeStr = localTimeStr.copy(selection = TextRange(2))
                            timeFocusRequester.requestFocus()
                        })

                        if (!is24Hour) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isPm = time.hour >= 12
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val newHour = if (isPm) time.hour - 12 else time.hour + 12
                                        onTimeChange(time.withHour(newHour))
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                TextMMD(if (isPm) "PM" else "AM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateSegment(value: String, cursor: Int, offset: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.clickable { onClick() }) {
        DigitBox(value.getOrNull(0)?.toString() ?: "", cursor == offset)
        DigitBox(value.getOrNull(1)?.toString() ?: "", cursor == offset + 1)
    }
}

@Composable
fun DigitBox(char: String, isHighlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 16.dp, height = 26.dp)
            .background(if (isHighlighted) Color.Black else Color.Transparent, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        TextMMD(
            text = char.ifEmpty { "0" },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) Color.White else Color.Black
        )
    }
}


@Composable
fun CompactOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        TextMMD(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun OptionRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    hasChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) Color.Black else Color.LightGray, modifier = Modifier.size(20.dp))
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextMMD(
                text = title,
                fontSize = 15.sp,
                color = if (enabled) Color.Black else Color.LightGray,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        if (checked != null && onCheckedChange != null) {
            SwitchMMD(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier
                    .scale(0.6f)
                    .padding(horizontal = 4.dp, vertical = 0.dp)
                    .size(20.dp)
            )
        } else if (hasChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
