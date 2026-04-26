package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarEvent
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.eInkVerticalScroll
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val events by viewModel.upcomingEvents.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text = "Agenda",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
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
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Event", modifier = Modifier.size(32.dp))
            }
        }
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

                    if (events.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TextMMD("No upcoming events", color = Color.Gray)
                        }
                    } else {
                        val groupedEvents = remember(events) { events.groupBy { it.start.toLocalDate() } }
                        AgendaList(
                            groupedEvents = groupedEvents,
                            onEventClick = { event ->
                                navController.navigate("event_detail/${event.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaList(
    groupedEvents: Map<LocalDate, List<CalendarEvent>>,
    onEventClick: (CalendarEvent) -> Unit
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val canScrollForward by remember { derivedStateOf { state.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { state.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .eInkVerticalScroll(state, scope, isScrollable),
            userScrollEnabled = false,
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            groupedEvents.forEach { (date, dayEvents) ->
                item(key = date) {
                    AgendaHeader(date)
                }
                items(dayEvents, key = { it.id }) { event ->
                    AgendaItem(event) { onEventClick(event) }
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.LightGray))
                }
            }
        }

        if (isScrollable) {
            EInkScrollbar(state = state, scope = scope)
        }
    }
}

@Composable
fun AgendaHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextMMD(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun AgendaItem(event: CalendarEvent, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
    val timeTxt = if (event.allDay) "All day" else {
        "${event.start.format(timeFormatter)} - ${event.end.format(timeFormatter)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        TextMMD(
            text = event.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
        TextMMD(
            text = timeTxt,
            fontSize = 13.sp,
            color = Color.DarkGray,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!event.location.isNullOrBlank()) {
            TextMMD(
                text = event.location,
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
