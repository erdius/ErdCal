package com.example.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val events by viewModel.monthEvents.collectAsState()
    val showWeekNumbers by viewModel.showWeekNumbers.collectAsState()
    val startDayMonday by viewModel.startWeekOnMonday.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.previousMonth() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        TextMMD(
                            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        IconButton(onClick = { viewModel.nextMonth() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("agenda") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.ViewAgenda, contentDescription = "Agenda", modifier = Modifier.size(24.dp))
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
                },
                showDivider = true
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (currentMonth != YearMonth.now()) {
                    FloatingActionButtonMMD(
                        onClick = { viewModel.goToToday() },
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Today,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextMMD(
                                text = "Today",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                FloatingActionButtonMMD(
                    onClick = {
                        viewModel.beginNewEvent()
                        navController.navigate("add_event?fromCalendar=true")
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event", modifier = Modifier.size(32.dp))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
        ) {
            CalendarPermissionGate(
                hasPermission = hasPermission,
                onPermissionGranted = { viewModel.refreshPermission() }
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.padding(vertical = 4.dp))
                    DaysOfWeekHeader(startDayMonday = startDayMonday, showWeekNumbers = showWeekNumbers)

                    val eventDays = remember(events) {
                        val set = mutableSetOf<LocalDate>()
                        events.forEach { ev ->
                            var d = ev.start.toLocalDate()
                            val last = if (ev.allDay) ev.end.toLocalDate().minusDays(1) else ev.end.toLocalDate()
                            while (!d.isAfter(last)) {
                                set.add(d)
                                d = d.plusDays(1)
                            }
                        }
                        set
                    }

                    CalendarGrid(
                        currentMonth = currentMonth,
                        eventDays = eventDays,
                        showWeekNumbers = showWeekNumbers,
                        startDayMonday = startDayMonday,
                        onDateSelected = { date ->
                            viewModel.onDateSelected(date)
                            navController.navigate("day_view")
                        }
                    )

                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
fun DaysOfWeekHeader(startDayMonday: Boolean, showWeekNumbers: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        if (showWeekNumbers) {
            Spacer(modifier = Modifier.width(32.dp)) // Same width as WeekNumberCell
        }
        val daysOfWeek = if (startDayMonday) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        } else {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        }
        daysOfWeek.forEach { day ->
            TextMMD(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    eventDays: Set<LocalDate>,
    showWeekNumbers: Boolean,
    startDayMonday: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
    
    // Offset calculation: 
    // If starting on Monday, offset is dayOfWeekValue - 1
    // If starting on Sunday, offset is dayOfWeekValue % 7
    val offset = if (startDayMonday) {
        dayOfWeekValue - 1
    } else {
        dayOfWeekValue % 7
    }

    val days = mutableListOf<LocalDate>()

    val prevMonth = currentMonth.minusMonths(1)
    val daysInPrevMonth = prevMonth.lengthOfMonth()
    for (i in offset - 1 downTo 0) {
        days.add(prevMonth.atDay(daysInPrevMonth - i))
    }

    val daysInMonth = currentMonth.lengthOfMonth()
    for (i in 1..daysInMonth) {
        days.add(currentMonth.atDay(i))
    }

    val totalCells = 42
    val remainingCells = totalCells - days.size
    for (i in 1..remainingCells) {
        days.add(currentMonth.plusMonths(1).atDay(i))
    }

    val weeks = days.chunked(7)
    val today = LocalDate.now()

    Column {
        weeks.forEachIndexed { index, weekDays ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showWeekNumbers) {
                    WeekNumberCell(weekDays.first())
                }
                weekDays.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        DayCell(
                            date = date,
                            isActiveDay = date == today,
                            isCurrentMonth = YearMonth.from(date) == currentMonth,
                            hasEvent = eventDays.contains(date),
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
            if (index < weeks.size - 1) {
                DashedDivider(showWeekNumbers = showWeekNumbers)
            }
        }
    }
}

@Composable
fun WeekNumberCell(firstDayOfWeek: LocalDate) {
    // ISO-8601 week number
    val weekFields = java.time.temporal.WeekFields.ISO
    val weekNum = firstDayOfWeek.get(weekFields.weekOfWeekBasedYear())
    
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        TextMMD(
            text = weekNum.toString(),
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DashedDivider(showWeekNumbers: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (showWeekNumbers) {
            Spacer(modifier = Modifier.width(32.dp))
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(1.dp)
        ) {
            drawLine(
                color = Color.Black,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    isActiveDay: Boolean,
    isCurrentMonth: Boolean,
    hasEvent: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onDateSelected(date) },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = if (isActiveDay) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                TextMMD(
                    text = date.dayOfMonth.toString(),
                    color = if (isActiveDay) Color.White else (if (isCurrentMonth) Color.Black else Color.LightGray),
                    fontWeight = if (isActiveDay || isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (hasEvent) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color.Black, CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.size(4.dp))
            }
        }
    }
}
