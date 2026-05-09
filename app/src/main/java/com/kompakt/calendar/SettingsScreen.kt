package com.kompakt.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kompakt.calendar.ui.EInkScrollbar
import com.kompakt.calendar.ui.common.DashedDivider
import com.kompakt.calendar.ui.eInkVerticalScroll
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
    val useAmericanDateFormat by viewModel.useAmericanDateFormat.collectAsState()
    val calendars by viewModel.calendarsLive.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()
    val defaultReminderMinutes by viewModel.defaultReminderMinutes.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val scope = rememberCoroutineScope()
    var showReminderPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }
    val isDuraSpeedAvailable = remember {
        try {
            context.packageManager.getPackageInfo("com.mediatek.duraspeed", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

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

    var resumeToggle by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermission()
                resumeToggle = !resumeToggle
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .eInkVerticalScroll(listState, scope, isScrollable),
                userScrollEnabled = false
            ) {

                // Permissions Status section
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TextMMD(
                        text = "System Permissions & Optimization",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    PermissionStatusRow(
                        title = "Calendar Access",
                        isGranted = hasPermission,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                item {
                    key(resumeToggle) {
                        val notificationsGranted =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else {
                                context.getSystemService(NotificationManager::class.java)
                                    .areNotificationsEnabled()
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
                    }
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item {
                        key(resumeToggle) {
                            val alarmManager = context.getSystemService(AlarmManager::class.java)
                            PermissionStatusRow(
                                title = "Exact Alarms",
                                isGranted = alarmManager.canScheduleExactAlarms(),
                                onClick = {
                                    val intent =
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                    item {
                        HorizontalDividerMMD(
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                item {
                    key(resumeToggle) {
                        val powerManager = context.getSystemService(PowerManager::class.java)
                        val isIgnoringBatteryOptimizations =
                            powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        PermissionStatusRow(
                            title = "Battery Optimization",
                            isGranted = isIgnoringBatteryOptimizations,
                            subtitle = if (isIgnoringBatteryOptimizations) "Disabled (Recommended)" else "Enabled (May delay reminders)",
                            isWarning = !isIgnoringBatteryOptimizations,
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                if (isDuraSpeedAvailable) {
                    item {
                        key(resumeToggle) {
                            PermissionStatusRow(
                                title = "DuraSpeed",
                                isGranted = false,
                                subtitle = "Ensure KompaktCalendar is toggled ON in DuraSpeed settings",
                                isWarning = true,
                                onClick = {
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage("com.mediatek.duraspeed")
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            // Fallback to explicit component
                                            val explicitIntent = Intent().apply {
                                                component = ComponentName("com.mediatek.duraspeed", "com.mediatek.duraspeed.DuraSpeedMainActivity")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(explicitIntent)
                                        }
                                    } catch (e: Exception) {
                                        // If all direct attempts fail, open App Info which often has a link to DuraSpeed
                                        try {
                                            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", "com.mediatek.duraspeed", null)
                                            }
                                            context.startActivity(appInfoIntent)
                                        } catch (e2: Exception) {
                                            // Final fallback: open general settings
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                item {
                    key(resumeToggle) {
                        val canDrawOverlays = Settings.canDrawOverlays(context)
                        PermissionStatusRow(
                            title = "Display over other apps",
                            isGranted = canDrawOverlays,
                            subtitle = if (canDrawOverlays) "Allowed" else "Needed for full-screen alerts",
                            isWarning = !canDrawOverlays,
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Calendar visibility section
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TextMMD(
                        text = "Calendars",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (calendars.isEmpty()) {
                    item {
                        TextMMD(
                            text = "No calendars found. Install and set up DAVx5 to add CalDAV accounts, " +
                                    "or sign in with Google.",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    itemsIndexed(calendars) { index, cal ->
                        CalendarToggleRow(
                            calendar = cal,
                            onToggle = { visible ->
                                scope.launch {
                                    viewModel.setCalendarVisibility(cal.id, visible)
                                }
                            }
                        )
                        if (index < calendars.size - 1) {
                            DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Display preferences
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TextMMD(
                        text = "Display",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    SettingToggle(
                        title = "Show week numbers",
                        checked = showWeekNumbers,
                        onCheckedChange = { scope.launch { viewModel.setShowWeekNumbers(it) } }
                    )
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                item {
                    SettingToggle(
                        title = "Start week on Monday",
                        checked = startDayMonday,
                        onCheckedChange = { scope.launch { viewModel.setStartWeekOnMonday(it) } }
                    )
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                item {
                    SettingToggle(
                        title = "Use American date format",
                        checked = useAmericanDateFormat,
                        onCheckedChange = { scope.launch { viewModel.setUseAmericanDateFormat(it) } }
                    )
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Default Calendar section
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TextMMD(
                        text = "Default Calendar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (calendars.isEmpty()) {
                    item {
                        TextMMD(
                            text = "No calendars available.",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    val writableCalendars = calendars.filter { it.isWritable }
                    if (writableCalendars.isEmpty()) {
                        item {
                            TextMMD(
                                text = "No writable calendars found.",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        itemsIndexed(writableCalendars) { index, cal ->
                            DefaultCalendarRow(
                                calendar = cal,
                                isSelected = cal.id == defaultCalendarId,
                                onClick = {
                                    scope.launch {
                                        viewModel.setDefaultCalendar(cal.id)
                                    }
                                }
                            )
                            if (index < writableCalendars.size - 1) {
                                DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Default Reminder section
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TextMMD(
                        text = "Default Reminder",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReminderPicker = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextMMD(
                            text = reminderOptions.find { it.second == defaultReminderMinutes }?.first
                                ?: "No reminder",
                            fontSize = 16.sp
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(180f)
                        ) // Chevron right
                    }
                }

                item {
                    DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // About section
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item {
                    TextMMD(
                        text = "About",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    TextMMD(
                        text = "KompaktCalendar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item {
                    TextMMD(
                        text = "Version 1.0.0\n\n" +
                                "Built with Mudita Mindful Design for e-ink devices.\n\n" +
                                "Reads calendars synced by DAVx5 (CalDAV), Google Calendar, Exchange, " +
                                "and any other Android sync adapter.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        lineHeight = 16.sp
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }

            if (isScrollable) {
                EInkScrollbar(state = listState, scope = scope)
            }
        }
    }
}

@Composable
private fun CalendarToggleRow(
    calendar: com.kompakt.calendar.calendar.CalendarAccount,
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
                fontSize = 12.sp
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val isScrollable by remember { derivedStateOf { canScrollForward || canScrollBackward } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
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

            HorizontalDividerMMD(thickness = 1.dp)

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .eInkVerticalScroll(listState, scope, isScrollable)
                        .padding(16.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(options) { index, (label, minutes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(minutes) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextMMD(
                                label,
                                fontSize = 18.sp,
                                fontWeight = if (selectedMinutes == minutes) FontWeight.Bold else FontWeight.Normal
                            )
                            RadioButtonMMD(
                                selected = selectedMinutes == minutes,
                                onClick = { onPick(minutes) }
                            )
                        }
                        if (index < options.size - 1) {
                            DashedDivider()
                        }
                    }
                }
                if (isScrollable) {
                    EInkScrollbar(state = listState, scope = scope)
                }
            }
        }
    }
}

@Composable
private fun DefaultCalendarRow(
    calendar: com.kompakt.calendar.calendar.CalendarAccount,
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
                fontSize = 12.sp
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
                TextMMD(text = subtitle, fontSize = 12.sp)
            } else {
                TextMMD(
                    text = if (isGranted) "Granted" else "Not granted (Tap to fix)",
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else if (isWarning) Icons.Default.Warning else Icons.Default.Error,
            contentDescription = null,
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
