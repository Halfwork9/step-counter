package com.example.stepcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(Store.FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Store.TRACKING_ENABLED, true)) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(Intent(context, StepService::class.java))
            } else {
                context.startService(Intent(context, StepService::class.java))
            }
        } catch (_: Exception) {
            // Blocked on some Android versions – restarts when the app is opened instead
        }
    }
}
