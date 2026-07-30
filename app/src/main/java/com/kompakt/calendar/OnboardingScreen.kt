package com.kompakt.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    
    val isDuraSpeedAvailable = remember {
        try {
            context.packageManager.getPackageInfo("com.mediatek.duraspeed", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

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
        
        if (calendarGranted) {
            viewModel.refreshPermission()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = canFinish,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    TextMMD(
                        text = if (canFinish) "Start using ErdCal" else "Grant Calendar Access to start",
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
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .eInkVerticalScroll(listState, scope, isScrollable),
                userScrollEnabled = false,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextMMD(
                        text = "Welcome",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextMMD(
                        text = "We need these permissions for reliable e-ink reminders.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    OnboardingPermissionRow(
                        title = "Calendar Access",
                        description = "Manage your events.",
                        isGranted = calendarGranted,
                        isRequired = true,
                        onClick = {
                            calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Notifications",
                        description = "Event reminders.",
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
                    HorizontalDividerMMD(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Exact Alarms",
                        description = "Precise timing.",
                        isGranted = alarmsGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Overlay",
                        description = "Full-screen alerts.",
                        isGranted = overlayGranted,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDividerMMD(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }

                item {
                    OnboardingPermissionRow(
                        title = "Battery",
                        description = "No delayed alerts.",
                        isGranted = batteryGranted,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )
                    if (isDuraSpeedAvailable) {
                        HorizontalDividerMMD(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                if (isDuraSpeedAvailable) {
                    item {
                        OnboardingPermissionRow(
                            title = "DuraSpeed",
                            description = "Whitelisting for ErdCal.",
                            isGranted = false,
                            onClick = {
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage("com.mediatek.duraspeed")
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    val explicitIntent = Intent().apply {
                                        component = ComponentName("com.mediatek.duraspeed", "com.mediatek.duraspeed.DuraSpeedMainActivity")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(explicitIntent)
                                }
                            } catch (e: Exception) {
                                try {
                                    val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", "com.mediatek.duraspeed", null)
                                    }
                                    context.startActivity(appInfoIntent)
                                } catch (e2: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        }
                        )
                    }
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (isRequired && !isGranted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextMMD(text = "(Req.)", fontSize = 11.sp)
                }
            }
            TextMMD(text = description, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else if (isRequired) Icons.Default.Error else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }
}
