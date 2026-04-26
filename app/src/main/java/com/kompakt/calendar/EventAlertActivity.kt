package com.kompakt.calendar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.kompakt.calendar.calendar.CalendarEvent
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
        if (eventId == -1L) {
            finish()
            return
        }

        val app = application as MyApplication
        val repo = app.calendarRepository

        setContent {
            KompaktCalendarTheme {
                var event by remember { mutableStateOf<CalendarEvent?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(eventId) {
                    scope.launch {
                        event = repo.getEventById(eventId)
                        if (event == null) finish()
                    }
                }

                if (event != null) {
                    AlertContent(
                        event = event!!,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    @Composable
    private fun AlertContent(event: CalendarEvent, onClose: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"

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
                    modifier = Modifier.size(36.dp),
                    tint = Color.Black
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                        modifier = Modifier.size(48.dp),
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                TextMMD(
                    text = event.title.ifBlank { "Untitled event" },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextMMD(
                    text = event.start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)),
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

                // Dashed Divider
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                ) {
                    drawLine(
                        color = Color.Black,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // Bottom Close Button
            ButtonMMD(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .width(220.dp)
                    .height(64.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
            ) {
                TextMMD(
                    text = "Close",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
