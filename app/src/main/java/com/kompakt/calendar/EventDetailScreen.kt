package com.kompakt.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.calendar.CalendarEvent
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    navController: NavController,
    eventId: Long,
    viewModel: CalendarViewModel = viewModel()
) {
    var event by remember { mutableStateOf<CalendarEvent?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventId) {
        scope.launch {
            event = viewModel.loadEventById(eventId)
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(32.dp))
                    }
                },
                title = {
                    TextMMD(text = "Event Preview", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.deleteEventById(eventId)
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = {
                            event?.let { ev ->
                                viewModel.beginEditEvent(ev)
                                navController.navigate("add_event?fromCalendar=false")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(30.dp))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (event == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                TextMMD("Event not found")
            }
        } else {
            val ev = event!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                HorizontalDividerMMD(thickness = 2.dp)

                Spacer(modifier = Modifier.height(24.dp))

                TextMMD(
                    text = ev.title.ifBlank { "Untitled event" },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                val is24Hour = DateFormat.is24HourFormat(context)
                val timePattern = if (is24Hour) "HH:mm" else "h:mm a"

                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.5.dp, Color.Black, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        TextMMD(
                            text = ev.start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        )
                        val timeRange = if (ev.allDay) {
                            "All day"
                        } else {
                            "${ev.start.format(DateTimeFormatter.ofPattern(timePattern, Locale.US))} – ${ev.end.format(DateTimeFormatter.ofPattern(timePattern, Locale.US))}"
                        }
                        TextMMD(
                            text = timeRange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val rrule = ev.rrule
                if (!rrule.isNullOrBlank()) {
                    val recurrenceText = when {
                        rrule.contains("FREQ=DAILY") -> "Daily"
                        rrule.contains("INTERVAL=2") && rrule.contains("FREQ=WEEKLY") -> "Bi-weekly"
                        rrule.contains("FREQ=WEEKLY") -> "Weekly"
                        rrule.contains("FREQ=MONTHLY") -> "Monthly"
                        rrule.contains("FREQ=YEARLY") -> "Yearly"
                        else -> "Recurring"
                    }
                    DetailRow(label = "Repeat", value = recurrenceText)
                }

                val location = ev.location
                if (!location.isNullOrBlank()) {
                    DetailRow(label = "Location", value = location)
                }

                TextMMD(
                    text = "Calendar: ${ev.calendarDisplayName}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (ev.hasReminder) {
                    val reminderText = when (val mins = ev.reminderMinutes) {
                        null -> "5 minutes before"
                        0 -> "At time of event"
                        in 1..59 -> "$mins minutes before"
                        in 60..1439 -> {
                            val hours = mins / 60
                            val m = mins % 60
                            if (m == 0) "$hours ${if (hours == 1) "hour" else "hours"} before"
                            else "${hours}h ${m}m before"
                        }
                        else -> {
                            val days = mins / 1440
                            "$days ${if (days == 1) "day" else "days"} before"
                        }
                    }
                    DetailRow(label = "Reminder", value = reminderText)
                }

                val description = ev.description
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    TextMMD(
                        text = "Notes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextMMD(
                        text = description,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextMMD(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        TextMMD(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
