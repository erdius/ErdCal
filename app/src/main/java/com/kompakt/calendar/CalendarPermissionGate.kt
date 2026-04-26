package com.kompakt.calendar

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalendarPermissionGate(
    hasPermission: Boolean,
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var requested by remember { mutableStateOf(false) }

    val am = remember { context.getSystemService(AlarmManager::class.java) }
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    val isIgnoringBatteryOptimizations = remember(requested) {
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    val canScheduleExact = remember(requested) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am?.canScheduleExactAlarms() ?: true
        } else true
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        requested = true
        val granted = result[Manifest.permission.READ_CALENDAR] == true
        if (granted) onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !requested) {
            val permissions = mutableListOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(permissions.toTypedArray())
        }
    }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Black
            )
            Spacer(Modifier.height(24.dp))
            TextMMD(
                "Calendar access needed",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            TextMMD(
                "KompaktCalendar reads events from your device's calendars, including any " +
                        "synced via DAVx5 (CalDAV), Google, or Exchange. Grant calendar " +
                        "access to continue.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ButtonMMD(
                            onClick = {
                                if (!isIgnoringBatteryOptimizations) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } else if (requested || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact)) {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    } else {
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val permissions = mutableListOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    launcher.launch(permissions.toTypedArray())
                                }
                            }
                        ) {
                            TextMMD(
                                if (!isIgnoringBatteryOptimizations) {
                                    "Disable Battery Optimization"
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
                                    "Allow Exact Alarms"
                                } else if (requested) {
                                    "Open Settings"
                                } else {
                                    "Grant access"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isIgnoringBatteryOptimizations) {
                            Spacer(Modifier.height(8.dp))
                            TextMMD(
                                "To ensure notifications arrive on time, please disable battery optimization for this app.",
                                fontSize = 12.sp,
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
                            Spacer(Modifier.height(8.dp))
                            TextMMD(
                                "Note: Android 12 requires explicit permission to schedule exact event reminders.",
                                fontSize = 12.sp,
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            Spacer(Modifier.height(16.dp))
            TextMMD(
                "Tip: install DAVx5 from F-Droid or Play Store to sync any CalDAV server " +
                        "(Nextcloud, Posteo, mailbox.org, …). Once a CalDAV account is added, " +
                        "its calendars appear here automatically.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
