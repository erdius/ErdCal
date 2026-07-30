package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarEvent
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val agendaPage by viewModel.agendaPage.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val events by viewModel.dayEvents.collectAsState()
    val useAmericanDateFormat by viewModel.useAmericanDateFormat.collectAsState()

    var eventColumnOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedDate) { eventColumnOffset = 0 }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.previousDay() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day", modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
                            TextMMD(
                                text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                fontSize = 12.sp,
                                lineHeight = 12.sp
                            )
                            val dateText = if (useAmericanDateFormat) {
                                "${selectedDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedDate.dayOfMonth}"
                            } else {
                                "${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                            }
                            TextMMD(
                                text = dateText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )
                        }
                        IconButton(onClick = { viewModel.nextDay() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day", modifier = Modifier.size(24.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("calendar") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Calendar", modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { navController.navigate("event_search") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { navController.navigate("settings") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMMD(
                onClick = {
                    viewModel.beginNewEvent()
                    navController.navigate("add_event?fromCalendar=false")
                },
                modifier = Modifier.padding(end = 0.dp, bottom = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Event", modifier = Modifier.size(32.dp))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CalendarPermissionGate(
                hasPermission = hasPermission,
                onPermissionGranted = { viewModel.refreshPermission() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(agendaPage) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    if (totalDrag > 50) {
                                        if (agendaPage > 0) viewModel.setAgendaPage(agendaPage - 1)
                                    } else if (totalDrag < -50) {
                                        if (agendaPage < 2) viewModel.setAgendaPage(agendaPage + 1)
                                    }
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val allDay = events.filter { it.allDay }
                        if (allDay.isNotEmpty()) {
                            AllDayBar(allDay) { ev ->
                                val time = ev.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                navController.navigate("event_detail/${ev.id}?instanceTime=$time")
                            }
                        }

                        val hours = getHoursForPage(agendaPage)
                        val timedEvents = events.filter { !it.allDay }

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val slotHeight = this.maxHeight / 12

                            Column(modifier = Modifier.fillMaxSize()) {
                                hours.forEach { hour ->
                                    TimeSlotLabel(hour, slotHeight)
                                }
                            }

                            TimeGridOverlay(
                                hours = hours,
                                slotHeight = slotHeight,
                                date = selectedDate,
                                events = timedEvents,
                                columnOffset = eventColumnOffset,
                                onColumnOffsetChange = { eventColumnOffset = it },
                                onEventClick = { ev ->
                                    val time = ev.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    navController.navigate("event_detail/${ev.id}?instanceTime=$time")
                                },
                                onEventMoved = { ev, newTime ->
                                    val newStart = selectedDate.atTime(newTime)
                                    scope.launch {
                                        viewModel.moveEvent(ev, newStart)
                                    }
                                },
                                onLongPress = { time ->
                                    viewModel.beginNewEvent(date = selectedDate, time = time)
                                    navController.navigate("add_event?fromCalendar=false")
                                }
                            )
                        }
                    }

                    DayViewScrollIndicator(
                        currentPage = agendaPage,
                        onPageSelected = { viewModel.setAgendaPage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayBar(events: List<CalendarEvent>, onEventClick: (CalendarEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        TextMMD("All-day", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        events.forEach { ev ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                    .clickable { onEventClick(ev) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                TextMMD(ev.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDividerMMD(thickness = 1.dp)
    }
}

@Composable
fun TimeSlotLabel(hour: Int, height: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    val displayHour = if (is24Hour) {
        String.format(Locale.US, "%02d:00", hour)
    } else {
        when {
            hour == 0 -> "12 am"
            hour < 12 -> "$hour am"
            hour == 12 -> "12 pm"
            else -> "${hour - 12} pm"
        }
    }

    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = 8.dp)
    ) {
        TextMMD(
            text = displayHour,
            fontSize = 13.sp,
            modifier = Modifier
                .width(52.dp)
                .align(Alignment.TopStart)
                .padding(top = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(start = 48.dp, end = 8.dp)
                .align(Alignment.TopStart)
                .offset(y = 14.dp)
        ) {
            drawLine(
                color = outline,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}

@Composable
fun TimeGridOverlay(
    hours: List<Int>,
    slotHeight: Dp,
    date: LocalDate,
    events: List<CalendarEvent>,
    columnOffset: Int,
    onColumnOffsetChange: (Int) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onEventMoved: (CalendarEvent, LocalTime) -> Unit,
    onLongPress: (LocalTime) -> Unit
) {
    if (hours.isEmpty()) return
    val startHour = hours.first()
    val endHour = hours.last()
    val rangeStart = LocalTime.of(startHour, 0)
    val rangeEnd = if (endHour == 23) LocalTime.MAX else LocalTime.of(endHour + 1, 0)

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTime = LocalTime.now()
        }
    }

    val visibleEvents = events.filter { ev ->
        val s = if (ev.start.toLocalDate().isBefore(date)) LocalTime.MIN else ev.start.toLocalTime()
        val e = if (ev.end.toLocalDate().isAfter(date)) LocalTime.MAX else ev.end.toLocalTime()
        s < rangeEnd && e > rangeStart
    }

    val groups = mutableListOf<MutableList<CalendarEvent>>()
    visibleEvents.sortedBy { it.start }.forEach { ev ->
        var added = false
        for (group in groups) {
            if (group.any { overlaps(it, ev, date) }) {
                group.add(ev)
                added = true
                break
            }
        }
        if (!added) groups.add(mutableListOf(ev))
    }

    // Max simultaneous overlap across all groups this page
    val maxGroupSize = groups.maxOfOrNull { it.size } ?: 0
    val hasOverflow = maxGroupSize > 2
    // positions: 0 .. (maxGroupSize - 2), each showing columns [pos, pos+1]
    val maxColumnOffset = (maxGroupSize - 2).coerceAtLeast(0)

    var isDraggingH by remember { mutableStateOf(false) }
    val latestColumnOffset by rememberUpdatedState(columnOffset)
    val latestMaxColumnOffset by rememberUpdatedState(maxColumnOffset)
    val latestHasOverflow by rememberUpdatedState(hasOverflow)
    val latestOnChange by rememberUpdatedState(onColumnOffsetChange)
    // onLongPress closes over the currently viewed date; without this, the
    // pointerInput(Unit) below (installed once) would freeze it to whatever
    // day was showing on first composition, silently misfiling new events
    // added after navigating to a different day.
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestRangeStart by rememberUpdatedState(rangeStart)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val minutes = ((offset.y - 14.dp.toPx()) / slotHeight.toPx()) * 60
                        val snappedMinutes = ((minutes + 7.5f) / 15).toInt() * 15
                        val time = latestRangeStart.plusMinutes(snappedMinutes.toLong())
                        latestOnLongPress(time)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { isDraggingH = false },
                    onDragCancel = { isDraggingH = false }
                ) { _, dragAmount ->
                    if (!isDraggingH && latestHasOverflow) {
                        isDraggingH = true
                        // swipe left (dragAmount < 0) → show columns further right
                        val newOffset = if (dragAmount < 0) {
                            (latestColumnOffset + 1).coerceAtMost(latestMaxColumnOffset)
                        } else {
                            (latestColumnOffset - 1).coerceAtLeast(0)
                        }
                        if (newOffset != latestColumnOffset) latestOnChange(newOffset)
                    }
                }
            }
    ) {
        val totalWidth = this.maxWidth

        groups.forEach { group ->
            val count = group.size
            group.forEachIndexed { index, ev ->
                val itemWidth: Dp
                val leftOffset: Dp

                if (count <= 2) {
                    // Normal: all events side by side
                    itemWidth = totalWidth / count
                    leftOffset = itemWidth * index
                } else {
                    // Overflow: show 2 columns at a time (each half-width)
                    val visiblePos = index - columnOffset
                    if (visiblePos !in 0..1) return@forEachIndexed
                    itemWidth = totalWidth / 2
                    leftOffset = itemWidth * visiblePos
                }

                val evStart = if (ev.start.toLocalDate().isBefore(date)) LocalTime.MIN else ev.start.toLocalTime()
                val evEnd = if (ev.end.toLocalDate().isAfter(date)) LocalTime.MAX else ev.end.toLocalTime()
                val actualStart = if (evStart.isBefore(rangeStart)) rangeStart else evStart
                val actualEnd = if (evEnd.isAfter(rangeEnd)) rangeEnd else evEnd

                val minutesFromStart = ChronoUnit.MINUTES.between(rangeStart, actualStart).coerceAtLeast(0)
                val topOffsetInitial = 14.dp + slotHeight * (minutesFromStart / 60f)
                val durationMinutes = ChronoUnit.MINUTES.between(actualStart, actualEnd)
                val height = slotHeight * (durationMinutes / 60f)

                var dragOffset by remember { mutableStateOf(0f) }
                // pointerInput is keyed on ev.id, which stays the same across
                // different instances of a recurring event on different days,
                // so a plain capture of ev/actualStart/onEventMoved would
                // freeze to whichever instance first installed the gesture.
                val latestEv by rememberUpdatedState(ev)
                val latestActualStart by rememberUpdatedState(actualStart)
                val latestOnEventMoved by rememberUpdatedState(onEventMoved)

                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(height)
                        .offset(x = leftOffset, y = topOffsetInitial + dragOffset.dp)
                        .padding(1.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                        .pointerInput(ev.id) {
                            detectDragGestures(
                                onDragStart = { dragOffset = 0f },
                                onDragEnd = {
                                    val minutesDragged = (dragOffset.dp.toPx() / slotHeight.toPx()) * 60
                                    val totalMinutes = (latestActualStart.toSecondOfDay() / 60f) + minutesDragged
                                    val snappedMinutes = ((totalMinutes + 7.5f) / 15).toInt() * 15
                                    val finalMinutes = snappedMinutes.coerceIn(0, 1439)
                                    val newTime = LocalTime.of(finalMinutes / 60, finalMinutes % 60)
                                    latestOnEventMoved(latestEv, newTime)
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y / density
                                }
                            )
                        }
                        .clickable { onEventClick(ev) }
                        .padding(4.dp)
                ) {
                    Column {
                        TextMMD(ev.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (height.value > 25f) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val is24Hour = DateFormat.is24HourFormat(context)
                            val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
                            val timeFormatter = DateTimeFormatter.ofPattern(timePattern)
                            TextMMD(
                                "${ev.start.toLocalTime().format(timeFormatter)}",
                                fontSize = 9.sp,
                                lineHeight = 10.sp
                            )
                        }
                        if (height.value > 40f && !ev.location.isNullOrBlank()) {
                            TextMMD(
                                text = ev.location,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Current time indicator
        if (date == LocalDate.now()) {
            val now = currentTime
            if (now.isAfter(rangeStart) && now.isBefore(rangeEnd)) {
                val minutesFromStart = ChronoUnit.MINUTES.between(rangeStart, now)
                val topOffset = 14.dp + slotHeight * (minutesFromStart / 60f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = topOffset - 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(Color.Black, CircleShape))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                }
            }
        }

        // Horizontal column position indicator — only when overflow exists
        if (hasOverflow) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..maxColumnOffset) {
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 4.dp)
                            .background(
                                color = if (i == columnOffset) Color.Black else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(0.5.dp, Color.Black, RoundedCornerShape(2.dp))
                            .clickable {
                                if (i != columnOffset) onColumnOffsetChange(i)
                            }
                    )
                }
            }
        }
    }
}

fun overlaps(e1: CalendarEvent, e2: CalendarEvent, date: LocalDate): Boolean {
    val s1 = if (e1.start.toLocalDate().isBefore(date)) LocalTime.MIN else e1.start.toLocalTime()
    val f1 = if (e1.end.toLocalDate().isAfter(date)) LocalTime.MAX else e1.end.toLocalTime()
    val s2 = if (e2.start.toLocalDate().isBefore(date)) LocalTime.MIN else e2.start.toLocalTime()
    val f2 = if (e2.end.toLocalDate().isAfter(date)) LocalTime.MAX else e2.end.toLocalTime()
    return s1 < f2 && s2 < f1
}

@Composable
fun DayViewScrollIndicator(currentPage: Int, onPageSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier.width(24.dp).fillMaxHeight().padding(vertical = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 80.dp)
                    .background(
                        color = if (i == currentPage) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .clickable { onPageSelected(i) }
            )
            if (i < 2) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

fun getHoursForPage(page: Int): List<Int> {
    return when (page) {
        0 -> (0..11).toList()
        1 -> (8..19).toList()
        else -> (12..23).toList()
    }
}
