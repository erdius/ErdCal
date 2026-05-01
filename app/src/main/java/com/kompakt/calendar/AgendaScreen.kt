package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarEvent
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.common.DashedDivider
import com.kompakt.calendar.ui.eInkVerticalScroll
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
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
                },
                modifier = Modifier.padding(end = 16.dp, bottom = 8.dp)
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
                if (events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextMMD("No upcoming events")
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
                itemsIndexed(dayEvents, key = { _, e -> e.id }) { index, event ->
                    AgendaItem(event) { onEventClick(event) }
                    if (index < dayEvents.size - 1) {
                        DashedDivider(modifier = Modifier.padding(start = 16.dp))
                    }
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

    Column {
        TextMMD(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        HorizontalDividerMMD(thickness = 2.dp)
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
            fontWeight = FontWeight.SemiBold
        )
        TextMMD(
            text = timeTxt,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!event.location.isNullOrBlank()) {
            TextMMD(
                text = event.location,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
