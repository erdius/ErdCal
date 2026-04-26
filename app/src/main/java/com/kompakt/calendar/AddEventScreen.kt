package com.example.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.calendar.calendar.CalendarAccount
import com.mudita.mmd.components.buttons.ButtonMMD
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
    val selectedReminderMinutes by viewModel.draftReminderMinutes.collectAsState()

    val eventNote by viewModel.eventNote.collectAsState()
    val editingId by viewModel.editingEventId.collectAsState()
    val calendars by viewModel.calendarsLive.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    
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

    var isPickingFrom by remember { mutableStateOf(true) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val reminderOptions = listOf(
        "No reminder" to null,
        "5 minutes" to 5,
        "10 minutes" to 10,
        "15 minutes" to 15,
        "1 hour" to 60,
        "1 day" to 1440,
        "1 week" to 10080
    )

    val writableCalendars = remember(calendars) { calendars.filter { it.isWritable } }
    val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }

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
                                            calendarId = selectedCalendarId,
                                            description = eventNote
                                        ),
                                        reminderMinutes = selectedReminderMinutes
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    HeaderTab(
                        label = "FROM",
                        time = startTime,
                        date = startDate,
                        isSelected = isPickingFrom,
                        isAllDay = isAllDay,
                        onClick = { isPickingFrom = true },
                        modifier = Modifier.weight(1f)
                    )
                    HeaderTab(
                        label = "TO",
                        time = endTime,
                        date = endDate,
                        isSelected = !isPickingFrom,
                        isAllDay = isAllDay,
                        onClick = { isPickingFrom = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                EventPicker(
                    date = if (isPickingFrom) startDate else endDate,
                    time = if (isPickingFrom) startTime else endTime,
                    isAllDay = isAllDay,
                    onDateChange = {
                        if (isPickingFrom) {
                            viewModel.updateDraftStartDate(it)
                            if (endDate.isBefore(it)) viewModel.updateDraftEndDate(it)
                        } else {
                            viewModel.updateDraftEndDate(it)
                            if (it.isBefore(startDate)) viewModel.updateDraftStartDate(it)
                        }
                    },
                    onTimeChange = {
                        if (isPickingFrom) {
                            viewModel.updateDraftStartTime(it)
                            if (startDate == endDate && (endTime.isBefore(it) || endTime == it)) {
                                viewModel.updateDraftEndTime(it.plusHours(1))
                            }
                        } else {
                            viewModel.updateDraftEndTime(it)
                            if (startDate == endDate && it.isBefore(startTime)) {
                                viewModel.updateDraftStartTime(it.minusHours(1))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                OptionRow(
                    icon = Icons.Outlined.Today,
                    title = "All-day event",
                    checked = isAllDay,
                    onCheckedChange = { viewModel.updateDraftIsAllDay(it) }
                )

                HorizontalDividerMMD(thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black)

                OptionRow(
                    icon = Icons.Outlined.NotificationsNone,
                    title = reminderOptions.find { it.second == selectedReminderMinutes }?.first ?: "No reminder",
                    hasChevron = true,
                    onClick = { showReminderPicker = true },
                    enabled = !isAllDay,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDividerMMD(thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black)

                OptionRow(
                    icon = Icons.Default.CalendarMonth,
                    title = selectedCalendar?.let {
                        "${it.displayName}${if (it.isDavx5) " (DAVx5)" else ""}"
                    } ?: "Choose calendar",
                    hasChevron = true,
                    onClick = { showCalendarPicker = true },
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDividerMMD(thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black)

                OptionRow(
                    icon = Icons.Outlined.Description,
                    title = if (eventNote.isEmpty()) {
                        "Add notes"
                    } else {
                        val displayNote = eventNote.replace("\n", " ")
                        if (displayNote.length > 10) "${displayNote.take(10)}..." else displayNote
                    },
                    hasChevron = true,
                    onClick = { navController.navigate("notes") },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                /*
                if (isEdit) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDividerMMD(thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black)
                    OptionRow(
                        icon = Icons.Default.Delete,
                        title = "Delete event",
                        hasChevron = false,
                        onClick = {
                            scope.launch {
                                val ok = viewModel.deleteCurrentEvent()
                                if (ok) {
                                    navController.popBackStack()
                                } else {
                                    android.widget.Toast.makeText(context, "Could not delete event.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))*/
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
                selectedMinutes = selectedReminderMinutes,
                onPick = {
                    viewModel.updateDraftReminderMinutes(it)
                    showReminderPicker = false
                },
                onDismiss = { showReminderPicker = false }
            )
        }
    }
}

@Composable
private fun ReminderPickerOverlay(
    options: List<Pair<String, Int?>>,
    selectedMinutes: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                TextMMD(
                    "Set Reminder",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                options.forEach { (label, minutes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextMMD(label, fontSize = 18.sp, fontWeight = if (selectedMinutes == minutes) FontWeight.Bold else FontWeight.Normal)
                        RadioButtonMMD(
                            selected = selectedMinutes == minutes,
                            onClick = { onPick(minutes) }
                        )
                    }
                    HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                }
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (calendars.isEmpty()) {
                    TextMMD("No writable calendars found. Sign in via DAVx5 or Google to add one.")
                } else {
                    calendars.forEach { cal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(cal.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TextMMD(cal.displayName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
        }
    }
}

@Composable
fun HeaderTab(
    label: String,
    time: LocalTime,
    date: LocalDate,
    isSelected: Boolean,
    isAllDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalContext.current
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"

        TextMMD(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.Gray)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isAllDay) {
                TextMMD(
                    text = time.format(DateTimeFormatter.ofPattern(timePattern, Locale.US)).lowercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else Color.Gray
                )
            }
            TextMMD(
                text = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)),
                fontSize = if (isAllDay) 18.sp else 10.sp,
                fontWeight = if (isAllDay) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.Gray
            )
            if (isSelected) {
                Box(modifier = Modifier.width(60.dp).height(2.dp).background(Color.Black))
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun EventPicker(
    date: LocalDate,
    time: LocalTime,
    isAllDay: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PickerColumn(
            modifier = Modifier.weight(5f),
            label = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)),
            onUp = { onDateChange(date.minusDays(1)) },
            onDown = { onDateChange(date.plusDays(1)) },
            prevLabels = listOf(
                date.minusDays(2).let { if (it == LocalDate.now()) "Today" else it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) },
                date.minusDays(1).let { if (it == LocalDate.now()) "Today" else it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) }
            ),
            nextLabels = listOf(
                date.plusDays(1).let { if (it == LocalDate.now()) "Today" else it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) },
                date.plusDays(2).let { if (it == LocalDate.now()) "Today" else it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) }
            ),
            isDate = true
        )

        if (!isAllDay) {
            val context = LocalContext.current
            val is24Hour = DateFormat.is24HourFormat(context)

            if (is24Hour) {
                PickerColumn(
                    modifier = Modifier.weight(3f),
                    label = String.format(Locale.US, "%02d", time.hour),
                    onUp = { onTimeChange(time.minusHours(1)) },
                    onDown = { onTimeChange(time.plusHours(1)) },
                    prevLabels = listOf(
                        String.format(Locale.US, "%02d", time.minusHours(2).hour),
                        String.format(Locale.US, "%02d", time.minusHours(1).hour)
                    ),
                    nextLabels = listOf(
                        String.format(Locale.US, "%02d", time.plusHours(1).hour),
                        String.format(Locale.US, "%02d", time.plusHours(2).hour)
                    )
                )
            } else {
                val hourDisplay = if (time.hour == 0 || time.hour == 12) 12 else time.hour % 12
                val amPm = if (time.hour < 12) "AM" else "PM"

                PickerColumn(
                    modifier = Modifier.weight(3f),
                    label = String.format(Locale.US, "%02d", hourDisplay),
                    subLabel = amPm,
                    onUp = { onTimeChange(time.minusHours(1)) },
                    onDown = { onTimeChange(time.plusHours(1)) },
                    prevLabels = listOf(
                        String.format(Locale.US, "%02d", if (time.minusHours(2).hour == 0 || time.minusHours(2).hour == 12) 12 else time.minusHours(2).hour % 12),
                        String.format(Locale.US, "%02d", if (time.minusHours(1).hour == 0 || time.minusHours(1).hour == 12) 12 else time.minusHours(1).hour % 12)
                    ),
                    nextLabels = listOf(
                        String.format(Locale.US, "%02d", if (time.plusHours(1).hour == 0 || time.plusHours(1).hour == 12) 12 else time.plusHours(1).hour % 12),
                        String.format(Locale.US, "%02d", if (time.plusHours(2).hour == 0 || time.plusHours(2).hour == 12) 12 else time.plusHours(2).hour % 12)
                    )
                )
            }

            PickerColumn(
                modifier = Modifier.weight(2f),
                label = String.format(Locale.US, "%02d", time.minute),
                onUp = { onTimeChange(time.minusMinutes(15)) },
                onDown = { onTimeChange(time.plusMinutes(15)) },
                prevLabels = listOf(
                    String.format(Locale.US, "%02d", time.minusMinutes(30).minute),
                    String.format(Locale.US, "%02d", time.minusMinutes(15).minute)
                ),
                nextLabels = listOf(
                    String.format(Locale.US, "%02d", time.plusMinutes(15).minute),
                    String.format(Locale.US, "%02d", time.plusMinutes(30).minute)
                )
            )
        }
    }
}

@Composable
fun PickerColumn(
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String? = null,
    onUp: () -> Unit,
    onDown: () -> Unit,
    prevLabels: List<String>,
    nextLabels: List<String>,
    isDate: Boolean = false
) {
    val currentOnUp by rememberUpdatedState(onUp)
    val currentOnDown by rememberUpdatedState(onDown)
    var hasTriggeredForGesture by remember { mutableStateOf(false) }
    var accumulatedDrag by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        hasTriggeredForGesture = false
                        accumulatedDrag = 0f
                    },
                    onDragEnd = {
                        hasTriggeredForGesture = false
                        accumulatedDrag = 0f
                    },
                    onDragCancel = {
                        hasTriggeredForGesture = false
                        accumulatedDrag = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!hasTriggeredForGesture) {
                            accumulatedDrag += dragAmount
                            if (accumulatedDrag > 50f) { // Swipe Down -> Pulls previous items into view
                                currentOnUp()
                                hasTriggeredForGesture = true
                            } else if (accumulatedDrag < -50f) { // Swipe Up -> Pulls next items into view
                                currentOnDown()
                                hasTriggeredForGesture = true
                            }
                        }
                        change.consume()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onUp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowDropUp, contentDescription = "Up", modifier = Modifier.size(48.dp))
        }

        prevLabels.forEach {
            TextMMD(it, fontSize = 12.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(2.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                TextMMD(
                    text = label,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subLabel != null) {
                    TextMMD(
                        text = subLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }

        nextLabels.forEach {
            Spacer(modifier = Modifier.height(2.dp))
            TextMMD(it, fontSize = 12.sp, color = Color.Black)
        }

        IconButton(onClick = onDown, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Down", modifier = Modifier.size(48.dp))
        }
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) Color.Black else Color.LightGray, modifier = Modifier.size(24.dp))
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextMMD(
                text = title,
                fontSize = 18.sp,
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
                    .scale(0.75f)
                    .padding(0.dp)
                    .size(40.dp)
            )
        } else if (hasChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}
