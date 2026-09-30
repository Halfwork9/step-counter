package com.example.stepcounter

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private var hasSensor = true
    private var testStart = -1
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(Store.FILE, MODE_PRIVATE)

        hasSensor = (getSystemService(SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        findViewById<Button>(R.id.btnTrack).setOnClickListener {
            if (isServiceRunning()) {
                prefs.edit().putBoolean(Store.TRACKING_ENABLED, false).apply()
                stopService(Intent(this, StepService::class.java))
                refresh()
            } else if (ensurePermissions()) {
                startTracking()
            }
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { toggleTest() }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(refreshTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED } && hasSensor
        ) {
            startTracking()
        }
    }

    private fun ensurePermissions(): Boolean {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.ACTIVITY_RECOGNITION

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS

        if (wanted.isNotEmpty()) {
            requestPermissions(wanted.toTypedArray(), 1)
            return false
        }
        return true
    }

    private fun startTracking() {
        if (!hasSensor) return
        val wasStoppedByUser = !prefs.getBoolean(Store.TRACKING_ENABLED, true)
        prefs.edit().putBoolean(Store.TRACKING_ENABLED, true).apply()
        if (wasStoppedByUser) {
            // skip the steps that happened while tracking was stopped
            prefs.edit().putBoolean(Store.RESET_BASELINE, true).apply()
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, StepService::class.java))
        else startService(Intent(this, StepService::class.java))
        refresh()
    }

    private fun toggleTest() {
        val current = prefs.getInt(Store.TODAY_STEPS, 0)
        val tvTest = findViewById<TextView>(R.id.tvTest)
        val btnTest = findViewById<Button>(R.id.btnTest)
        if (testStart < 0) {
            testStart = current
            btnTest.text = "⏹ End test walk"
            tvTest.visibility = View.VISIBLE
            tvTest.text = "Test walk: 0 steps — phone in pocket, start walking"
        } else {
            val walked = current - testStart
            testStart = -1
            btnTest.text = "🧪 Test walk"
            tvTest.text = "Last test: $walked steps counted"
        }
    }

    private fun refresh() {
        val goal = Store.goal(prefs)
        val steps = prefs.getInt(Store.TODAY_STEPS, 0)
        val activeMin = prefs.getInt(Store.ACTIVE_MIN, 0)

        findViewById<TextView>(R.id.tvSteps).text = String.format(Locale.getDefault(), "%,d", steps)
        val pct = if (goal > 0) steps * 100 / goal else 0
        findViewById<TextView>(R.id.tvGoal).text =
            "Goal ${String.format(Locale.getDefault(), "%,d", goal)} • ${if (steps >= goal) "reached 🎉" else "$pct%"}"

        val progress = findViewById<ProgressBar>(R.id.progressToday)
        progress.max = goal
        progress.progress = steps.coerceAtMost(goal)

        val strideKm = Store.heightCm(prefs) * 0.415 / 100.0 / 1000.0
        val km = steps * strideKm
        val kcal = (steps * 0.04 * Store.weightKg(prefs) / 70.0).toInt()
        val pace = if (activeMin > 0) steps / activeMin else 0
        findViewById<TextView>(R.id.tvDist).text = String.format(Locale.getDefault(), "%.2f", km)
        findViewById<TextView>(R.id.tvCal).text = String.format(Locale.getDefault(), "%,d", kcal)
        findViewById<TextView>(R.id.tvPace).text = if (pace > 0) pace.toString() else "—"

        val recognitionGranted = Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val running = isServiceRunning()
        val color: Int
        val text: String = when {
            !hasSensor -> { color = 0xFFF87171.toInt(); "No step sensor on this phone" }
            !recognitionGranted -> { color = 0xFFF87171.toInt(); "Physical activity permission needed" }
            running -> { color = 0xFF4ADE80.toInt(); "Tracking active" }
            else -> { color = 0xFFFBBF24.toInt(); "Paused" }
        }
        findViewById<TextView>(R.id.tvDot).setTextColor(color)
        findViewById<TextView>(R.id.tvStatus).apply {
            this.text = text
            setTextColor(color)
        }
        findViewById<Button>(R.id.btnTrack).text =
            if (running) "⏹ Stop tracking" else "▶ Start tracking"

        if (testStart >= 0) {
            findViewById<TextView>(R.id.tvTest).text = "Test walk: ${steps - testStart} steps so far"
        }

        val streak = computeStreak(goal)
        findViewById<TextView>(R.id.tvStreak).text =
            if (streak > 0) "🔥 ${streak}-day goal streak" else "No streak yet — hit your goal to start one"

        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val values = IntArray(7)
        val labels = Array(7) { "" }
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val key = dayFmt.format(c.time)
            values[6 - i] = if (i == 0) steps else prefs.getInt(Store.historyKey(key), 0)
            labels[6 - i] = labelFmt.format(c.time).take(1).uppercase()
        }
        findViewById<WeeklyChart>(R.id.chart).setData(values, labels, 6)
    }

    private fun computeStreak(goal: Int): Int {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var streak = 0
        for (offset in 0..365) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            val s = if (offset == 0) prefs.getInt(Store.TODAY_STEPS, 0)
                    else prefs.getInt(Store.historyKey(dayFmt.format(c.time)), 0)
            if (offset == 0 && s < goal) continue   // today still in progress
            if (s >= goal) streak++ else break
        }
        return streak
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == StepService::class.java.name
        }
    }

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etGoal = view.findViewById<EditText>(R.id.etGoal)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        etGoal.setText(Store.goal(prefs).toString())
        etHeight.setText(Store.heightCm(prefs).toString())
        etWeight.setText(Store.weightKg(prefs).toString())

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                etGoal.text.toString().toIntOrNull()?.let {
                    if (it in 1000..100000) prefs.edit().putInt(Store.GOAL, it).apply()
                }
                etHeight.text.toString().toIntOrNull()?.let {
                    if (it in 100..250) prefs.edit().putInt(Store.HEIGHT, it).apply()
                }
                etWeight.text.toString().toIntOrNull()?.let {
                    if (it in 30..250) prefs.edit().putInt(Store.WEIGHT, it).apply()
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
