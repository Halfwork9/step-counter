package com.example.stepcounter

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
    const val FILE = "steps"
    const val TODAY_STEPS = "today_steps"
    const val LAST_TOTAL = "last_total"
    const val DAY_KEY = "day_key"
    const val ACTIVE_MIN = "active_minutes"
    const val GOAL = "goal"
    const val HEIGHT = "height_cm"
    const val WEIGHT = "weight_kg"
    const val TRACKING_ENABLED = "tracking_enabled"
    const val RESET_BASELINE = "reset_baseline"

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayKey(): String = dayFmt.format(Date())
    fun historyKey(dayKey: String): String = "h_$dayKey"

    fun goal(p: SharedPreferences) = p.getInt(GOAL, 8000)
    fun heightCm(p: SharedPreferences) = p.getInt(HEIGHT, 170)
    fun weightKg(p: SharedPreferences) = p.getInt(WEIGHT, 70)
}
