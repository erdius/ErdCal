package com.example.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: CalendarViewModel = viewModel()
) {
    val context = LocalContext.current
    val showWeekNumbers by viewModel.showWeekNumbers.collectAsState()
    val startDayMonday by viewModel.startWeekOnMonday.collectAsState()
    val calendars by viewModel.calendarsLive.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    val scope = rememberCoroutineScope()
    var showReminderPicker by remember { mutableStateOf(false) }

    val reminderOptions = remember {
        listOf(
            "No reminder" to null,
            "5 minutes" to 5,
            "10 minutes" to 10,
            "15 minutes" to 15,
            "1 hour" to 60,
            "1 day" to 1440,
            "1 week" to 10080
        )
    }

    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    if (showReminderPicker) {
        ReminderPickerOverlay(
            options = reminderOptions,
            selectedMinutes = defaultReminderMinutes,
            onPick = {
                scope.launch {
                    viewModel.setDefaultReminderMinutes(it)
                    showReminderPicker = false
                }
            },
            onDismiss = { showReminderPicker = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                    }
                },
                title = {
                    TextMMD(text = "Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { paddingValues ->
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

            // Permissions Status section
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                text = "System Permissions & Optimization",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            PermissionStatusRow(
                title = "Calendar Access",
                isGranted = viewModel.hasPermission.collectAsState().value,
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            HorizontalDividerMMD(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray)

            val notificationsGranted = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                }
            }
            PermissionStatusRow(
                title = "Notifications",
                isGranted = notificationsGranted,
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    context.startActivity(intent)
                }
            )

            HorizontalDividerMMD(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(AlarmManager::class.java)
                PermissionStatusRow(
                    title = "Exact Alarms",
                    isGranted = alarmManager.canScheduleExactAlarms(),
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
                HorizontalDividerMMD(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray)
            }

            val powerManager = context.getSystemService(PowerManager::class.java)
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            PermissionStatusRow(
                title = "Battery Optimization",
                isGranted = isIgnoringBatteryOptimizations,
                subtitle = if (isIgnoringBatteryOptimizations) "Disabled (Recommended)" else "Enabled (May delay reminders)",
                isWarning = !isIgnoringBatteryOptimizations,
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDividerMMD(
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Black
            )

            // Calendar visibility section
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))
                TextMMD(
                    text = "Calendars",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (calendars.isEmpty()) {
                    TextMMD(
                        text = "No calendars found. Install and set up DAVx5 to add CalDAV accounts, " +
                                "or sign in with Google.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    calendars.forEach { cal ->
                        CalendarToggleRow(
                            calendar = cal,
                            onToggle = { visible ->
                                scope.launch {
                                    viewModel.setCalendarVisibility(cal.id, visible)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDividerMMD(
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.Black
                )
            }

            // Display preferences
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                text = "Display",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingToggle(
                title = "Show week numbers",
                checked = showWeekNumbers,
                onCheckedChange = { scope.launch { viewModel.setShowWeekNumbers(it) } }
            )

            HorizontalDividerMMD(
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Black
            )

            SettingToggle(
                title = "Start week on Monday",
                checked = startDayMonday,
                onCheckedChange = { scope.launch { viewModel.setStartWeekOnMonday(it) } }
            )

            HorizontalDividerMMD(
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Black
            )

            // Default Calendar section
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                text = "Default Calendar",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (calendars.isEmpty()) {
                TextMMD(
                    text = "No calendars available.",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                val writableCalendars = calendars.filter { it.isWritable }
                if (writableCalendars.isEmpty()) {
                    TextMMD(
                        text = "No writable calendars found.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    writableCalendars.forEach { cal ->
                        DefaultCalendarRow(
                            calendar = cal,
                            isSelected = cal.id == defaultCalendarId,
                            onClick = {
                                scope.launch {
                                    viewModel.setDefaultCalendar(cal.id)
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDividerMMD(
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Black
            )

            // Default Reminder section
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                text = "Default Reminder",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReminderPicker = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextMMD(
                    text = reminderOptions.find { it.second == defaultReminderMinutes }?.first ?: "No reminder",
                    fontSize = 16.sp
                )
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(24.dp).rotate(180f)) // Chevron right
            }

            HorizontalDividerMMD(
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Black
            )

            // About section
            Spacer(modifier = Modifier.height(32.dp))
            TextMMD(
                text = "About",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD(
                text = "KompaktCalendar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            TextMMD(
                text = "Version 1.0.0\n\n" +
                        "Built with Mudita Mindful Design for e-ink devices.\n\n" +
                        "Reads calendars synced by DAVx5 (CalDAV), Google Calendar, Exchange, " +
                        "and any other Android sync adapter.",
                fontSize = 12.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CalendarToggleRow(
    calendar: com.example.calendar.calendar.CalendarAccount,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(
                text = calendar.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            TextMMD(
                text = "${calendar.accountName}${if (calendar.isDavx5) " · DAVx5" else ""}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        SwitchMMD(
            checked = calendar.isVisible,
            onCheckedChange = onToggle,
            enabled = true
        )
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
                    "Set Default Reminder",
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
private fun DefaultCalendarRow(
    calendar: com.example.calendar.calendar.CalendarAccount,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(
                text = calendar.displayName,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            TextMMD(
                text = calendar.accountName,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        RadioButtonMMD(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    subtitle: String? = null,
    isWarning: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                TextMMD(text = subtitle, fontSize = 12.sp, color = if (isWarning && !isGranted) Color.Red else Color.Gray)
            } else {
                TextMMD(
                    text = if (isGranted) "Granted" else "Not granted (Tap to fix)",
                    fontSize = 12.sp,
                    color = if (isGranted) Color.DarkGray else Color.Red
                )
            }
        }
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else if (isWarning) Icons.Default.Warning else Icons.Default.Error,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else if (isWarning) Color(0xFFFFA000) else Color.Red,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextMMD(text = title, fontSize = 16.sp)
        SwitchMMD(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
