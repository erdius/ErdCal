package com.example.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import com.example.calendar.calendar.CalendarEvent
import com.mudita.mmd.components.buttons.ButtonMMD
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextMMD(text = "Event", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (event != null) {
                                viewModel.beginEditEvent(event!!)
                                navController.navigate("add_event?fromCalendar=false")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (event == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                TextMMD("Event not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.Black)
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextMMD(
                    text = event!!.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextMMD(
                    text = "Calendar: ${event!!.calendarDisplayName}",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                val is24Hour = DateFormat.is24HourFormat(context)
                val timePattern = if (is24Hour) "HH:mm" else "h:mm a"

                DetailRow(
                    label = if (event!!.allDay) "Date" else "Starts",
                    value = if (event!!.allDay)
                        event!!.start.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US))
                    else
                        event!!.start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · $timePattern", Locale.US))
                )

                if (!event!!.allDay || event!!.end.toLocalDate() != event!!.start.toLocalDate()) {
                    DetailRow(
                        label = "Ends",
                        value = if (event!!.allDay)
                            event!!.end.toLocalDate().minusDays(1)
                                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US))
                        else
                            event!!.end.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · $timePattern", Locale.US))
                    )
                }

                if (!event!!.location.isNullOrBlank()) {
                    DetailRow(label = "Location", value = event!!.location!!)
                }

                if (event!!.hasReminder) {
                    val reminderText = when (val mins = event!!.reminderMinutes) {
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

                if (!event!!.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    TextMMD(
                        text = "Notes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextMMD(
                        text = event!!.description!!,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                ButtonMMD(
                    onClick = {
                        scope.launch {
                            viewModel.deleteEventById(eventId)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    border = BorderStroke(1.5.dp, Color.Black)
                ) {
                    TextMMD("Delete event", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        TextMMD(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
