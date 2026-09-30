package com.example.stepcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(Intent(context, StepService::class.java))
                } else {
                    context.startService(Intent(context, StepService::class.java))
                }
            } catch (_: Exception) {
                // Some Android versions block this — the service restarts
                // next time the app is opened instead.
            }
        }
    }
}