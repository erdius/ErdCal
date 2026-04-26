package com.example.helloworld

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationScheduler.rescheduleAll(context)
            } finally {
                result.finish()
            }
        }
    }
}
