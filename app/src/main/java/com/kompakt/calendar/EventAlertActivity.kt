package com.kompakt.calendar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kompakt.calendar.calendar.CalendarEvent
import com.kompakt.calendar.ui.common.DashedDivider
import com.kompakt.calendar.ui.theme.KompaktCalendarTheme
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.*

class EventAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val eventId = intent.getLongExtra("event_id", -1L)
        val startMs = intent.getLongExtra("start_ms", -1L)
        val endMs = intent.getLongExtra("end_ms", -1L)

        if (eventId == -1L) {
            finish()
            return
        }

        val app = application as MyApplication
        val repo = app.calendarRepository

        setContent {
            KompaktCalendarTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                var event by remember { mutableStateOf<CalendarEvent?>(null) }
                val useAmericanDateFormat by (application as MyApplication).userPreferencesRepository.useAmericanDateFormat.collectAsState(initial = false)
                val textScale by (application as MyApplication).userPreferencesRepository.textScale.collectAsState(initial = 1.0f)
                val density = LocalDensity.current
                val scope = rememberCoroutineScope()

                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = textScale)
                ) {
                LaunchedEffect(eventId) {
                    scope.launch {
                        val baseEvent = repo.getEventById(eventId)
                        if (baseEvent == null) {
                            finish()
                            return@launch
                        }

                        // Override times if specific instance times were passed
                        if (startMs != -1L && endMs != -1L) {
                            val zone = java.time.ZoneId.systemDefault()
                            val instanceStart = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMs), zone)
                                .withSecond(0).withNano(0)
                            val instanceEnd = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMs), zone)
                                .withSecond(0).withNano(0)
                            event = baseEvent.copy(start = instanceStart, end = instanceEnd)
                        } else {
                            event = baseEvent
                        }
                    }
                }

                if (event != null) {
                    var showSnoozePicker by remember { mutableStateOf(false) }
                    var showCustomSnoozePicker by remember { mutableStateOf(false) }

                    AlertContent(
                        event = event!!,
                        useAmericanDateFormat = useAmericanDateFormat,
                        onClose = { finish() },
                        onSnoozeClick = { showSnoozePicker = true }
                    )

                    if (showSnoozePicker) {
                        SnoozePickerOverlay(
                            onSnooze = { minutes ->
                                val zone = java.time.ZoneId.systemDefault()
                                NotificationScheduler.scheduleSnooze(
                                    context,
                                    event!!.id,
                                    event!!.title,
                                    event!!.start.atZone(zone).toInstant().toEpochMilli(),
                                    event!!.end.atZone(zone).toInstant().toEpochMilli(),
                                    minutes
                                )
                                finish()
                            },
                            onCustomClick = {
                                showSnoozePicker = false
                                showCustomSnoozePicker = true
                            },
                            onDismiss = { showSnoozePicker = false }
                        )
                    }

                    if (showCustomSnoozePicker) {
                        CustomSnoozePickerOverlay(
                            onSnooze = { minutes ->
                                val zone = java.time.ZoneId.systemDefault()
                                NotificationScheduler.scheduleSnooze(
                                    context,
                                    event!!.id,
                                    event!!.title,
                                    event!!.start.atZone(zone).toInstant().toEpochMilli(),
                                    event!!.end.atZone(zone).toInstant().toEpochMilli(),
                                    minutes
                                )
                                finish()
                            },
                            onDismiss = { showCustomSnoozePicker = false }
                        )
                    }
                }
                }
            }
        }
    }

    @Composable
    private fun AlertContent(
        event: CalendarEvent,
        useAmericanDateFormat: Boolean,
        onClose: () -> Unit,
        onSnoozeClick: () -> Unit
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val datePattern = if (useAmericanDateFormat) "EEEE, MMMM d" else "EEEE, d MMMM"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Top Right Close Icon
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(top = 80.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Calendar Icon in rounded box
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                TextMMD(
                    text = event.title.ifBlank { "Untitled event" },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextMMD(
                    text = event.start.format(DateTimeFormatter.ofPattern(datePattern, Locale.US)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                val timeRange = if (event.allDay) {
                    "All day"
                } else {
                    "${event.start.format(DateTimeFormatter.ofPattern(timePattern, Locale.US))} – ${event.end.format(DateTimeFormatter.ofPattern(timePattern, Locale.US))}"
                }
                TextMMD(
                    text = timeRange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                DashedDivider(dashWidth = 5.dp, dashGap = 5.dp)

                Spacer(modifier = Modifier.weight(1f))

                // Snooze Button (Smaller)
                ButtonMMD(
                    onClick = onSnoozeClick,
                    modifier = Modifier
                        .width(180.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    TextMMD(
                        text = "Snooze...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button (Main Action)
                ButtonMMD(
                    onClick = onClose,
                    modifier = Modifier
                        .width(220.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                ) {
                    TextMMD(
                        text = "Close",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    @Composable
    private fun SnoozePickerOverlay(
        onSnooze: (Int) -> Unit,
        onCustomClick: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val options = listOf(
            "5 minutes" to 5,
            "10 minutes" to 10,
            "15 minutes" to 15,
            "30 minutes" to 30,
            "1 hour" to 60,
            "2 hours" to 120,
            "1 day" to 1440
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
                        "Snooze for...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    options.forEach { (label, minutes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSnooze(minutes) }
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextMMD(label, fontSize = 18.sp)
                        }
                        DashedDivider()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCustomClick() }
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextMMD("Custom...", fontSize = 18.sp, color = Color.Gray)
                    }
                }
            }
        }
    }

    @Composable
    private fun CustomSnoozePickerOverlay(
        onSnooze: (Int) -> Unit,
        onDismiss: () -> Unit
    ) {
        var amount by remember { mutableIntStateOf(10) }
        var unit by remember { mutableStateOf("minutes") }

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
                        "Custom Snooze",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    ButtonMMD(
                        onClick = {
                            val minutes = when (unit) {
                                "minutes" -> amount
                                "hours" -> amount * 60
                                "days" -> amount * 1440
                                else -> amount
                            }
                            onSnooze(minutes)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        TextMMD("Snooze", fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PickerColumn(
                            modifier = Modifier.width(100.dp),
                            label = amount.toString(),
                            onUp = { if (amount < 999) amount++ },
                            onDown = { if (amount > 1) amount-- },
                            prevLabels = listOf((amount - 2).toString(), (amount - 1).toString()).filter { it.toInt() > 0 },
                            nextLabels = listOf((amount + 1).toString(), (amount + 2).toString())
                        )

                        val units = listOf("minutes", "hours", "days")
                        val unitIndex = units.indexOf(unit)

                        PickerColumn(
                            modifier = Modifier.width(140.dp),
                            label = unit,
                            onUp = { unit = units[(unitIndex - 1 + units.size) % units.size] },
                            onDown = { unit = units[(unitIndex + 1) % units.size] },
                            prevLabels = listOf(units[(unitIndex - 2 + units.size) % units.size], units[(unitIndex - 1 + units.size) % units.size]),
                            nextLabels = listOf(units[(unitIndex + 1) % units.size], units[(unitIndex + 2) % units.size])
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    TextMMD("from now", fontSize = 16.sp)
                }
            }
        }
    }

    @Composable
    private fun PickerColumn(
        modifier: Modifier = Modifier,
        label: String,
        subLabel: String? = null,
        onUp: () -> Unit,
        onDown: () -> Unit,
        prevLabels: List<String>,
        nextLabels: List<String>
    ) {
        Column(
            modifier = modifier,
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
}


