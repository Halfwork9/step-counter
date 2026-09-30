package com.example.stepcounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Locale

class StepService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "step_counter"
        const val NOTIF_ID = 1001
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private val handler = Handler(Looper.getMainLooper())

    private var todaySteps = 0
    private var lastTotal = -1L
    private var activeMinutes = 0
    private var lastActiveMinute = -1L
    private var lastPersist = 0L

    private val ticker = object : Runnable {
        override fun run() {
            dayCheck()
            persist()
            updateNotification()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Store.FILE, MODE_PRIVATE)
        todaySteps = prefs.getInt(Store.TODAY_STEPS, 0)
        lastTotal = prefs.getLong(Store.LAST_TOTAL, -1L)
        activeMinutes = prefs.getInt(Store.ACTIVE_MIN, 0)

        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the user pressed Start after stopping, skip steps taken while paused
        if (prefs.getBoolean(Store.RESET_BASELINE, false)) {
            lastTotal = -1
            prefs.edit().putBoolean(Store.RESET_BASELINE, false).apply()
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val total = event.values.firstOrNull()?.toLong() ?: return
        if (lastTotal < 0) {           // first reading after install: start counting from now
            lastTotal = total
            return
        }
        var delta = total - lastTotal
        if (delta < 0) delta = total   // phone rebooted, sensor reset to 0
        lastTotal = total

        dayCheck()
        if (delta > 0) {
            todaySteps += delta.toInt()
            val minute = System.currentTimeMillis() / 60_000L
            if (minute != lastActiveMinute) {
                activeMinutes++
                lastActiveMinute = minute
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastPersist > 15_000L) {
            persist()
            updateNotification()
        }
    }

    /** Rolls over to a new day at midnight and saves yesterday's total. */
    private fun dayCheck() {
        val today = Store.dayKey()
        val stored = prefs.getString(Store.DAY_KEY, null)
        if (stored == null) {
            prefs.edit().putString(Store.DAY_KEY, today).apply()
        } else if (stored != today) {
            val editor = prefs.edit()
            if (todaySteps > 0) editor.putInt(Store.historyKey(stored), todaySteps)
            todaySteps = 0
            activeMinutes = 0
            lastActiveMinute = -1
            editor.putString(Store.DAY_KEY, today)
                .putInt(Store.TODAY_STEPS, 0)
                .putInt(Store.ACTIVE_MIN, 0)
                .apply()
        }
    }

    private fun persist() {
        lastPersist = System.currentTimeMillis()
        prefs.edit()
            .putInt(Store.TODAY_STEPS, todaySteps)
            .putLong(Store.LAST_TOTAL, lastTotal)
            .putInt(Store.ACTIVE_MIN, activeMinutes)
            .putString(Store.DAY_KEY, Store.dayKey())
            .apply()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val goal = Store.goal(prefs)
        val strideKm = Store.heightCm(prefs) * 0.415 / 100.0 / 1000.0
        val km = todaySteps * strideKm
        val pct = if (goal > 0) todaySteps * 100 / goal else 0
        val text = String.format(Locale.getDefault(), "%,d steps • %.2f km • %d%% of goal", todaySteps, km, pct)

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)

        return builder
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle("Step Counter")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF34D399.toInt())
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Step tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* ignore */ }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        persist()   // keep running even when app is swiped away
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(ticker)
        persist()
        super.onDestroy()
    }
}
