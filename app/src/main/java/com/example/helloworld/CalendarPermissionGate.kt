package com.example.helloworld

import android.Manifest
import android.content.Intent
import android.net.Uri
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

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        requested = true
        val granted = result[Manifest.permission.READ_CALENDAR] == true
        if (granted) onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !requested) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
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
            ButtonMMD(
                onClick = {
                    if (requested) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    } else {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                }
            ) {
                TextMMD(
                    if (requested) "Open Settings" else "Grant access",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
