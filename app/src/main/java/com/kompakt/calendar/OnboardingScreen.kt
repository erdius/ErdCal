package com.kompakt.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.eInkVerticalScroll
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: CalendarViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var calendarGranted by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }
    var alarmsGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var batteryGranted by remember { mutableStateOf(false) }

    fun refreshStatus() {
        calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
        alarmsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true
        overlayGranted = Settings.canDrawOverlays(context)
        batteryGranted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) { refreshStatus() }

    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshStatus()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshStatus()
    }

    val canFinish = calendarGranted

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ButtonMMD(
                    onClick = {
                        if (canFinish) {
                            scope.launch {
                                viewModel.setOnboardingCompleted(true)
                                onFinished()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = canFinish,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    TextMMD(
                        text = if (canFinish) "Start using KompaktCalendar" else "Grant Calendar Access to start",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        val isScrollable by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

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
                userScrollEnabled = false,
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    TextMMD(
                        text = "Welcome to KompaktCalendar",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextMMD(
                        text = "To provide a seamless e-ink experience and reliable reminders, we need a few permissions.",
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                item {
                    OnboardingPermissionRow(
                        title = "Calendar Access",
                        description = "Required to see and manage your events.",
                        isGranted = calendarGranted,
                        isRequired = true,
                        onClick = {
                            calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Notifications",
                        description = "To show you reminders for upcoming events.",
                        isGranted = notificationsGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Exact Alarms",
                        description = "Ensures reminders fire precisely on time, even in sleep mode.",
                        isGranted = alarmsGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Display over other apps",
                        description = "Allows full-screen alerts to wake up your screen for important events.",
                        isGranted = overlayGranted,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = Color.LightGray)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Battery Optimization",
                        description = "Prevents the system from delaying reminders to save power.",
                        isGranted = batteryGranted,
                        onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (isScrollable) {
                EInkScrollbar(state = listState, scope = scope)
            }
        }
    }
}

@Composable
private fun OnboardingPermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (isRequired && !isGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextMMD(text = "(Required)", fontSize = 12.sp, color = Color.Red)
                }
            }
            TextMMD(text = description, fontSize = 14.sp, color = Color.Gray, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else if (isRequired) Icons.Default.Error else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else if (isRequired) Color.Red else Color(0xFFFFA000),
            modifier = Modifier.size(32.dp)
        )
    }
}
