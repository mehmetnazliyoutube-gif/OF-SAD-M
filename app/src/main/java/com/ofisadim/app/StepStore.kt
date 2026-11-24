package com.ofisadim.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StepStore {
    private const val PREFS_NAME = "ofis_adim_native_prefs"
    private const val KEY_DAY = "day_key"
    private const val KEY_TODAY_STEPS = "today_steps"
    private const val KEY_LAST_RAW_COUNTER = "last_raw_counter"

    fun getTodayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @Synchronized
    fun getTodaySteps(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val savedDay = prefs.getString(KEY_DAY, "")
        if (savedDay != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_TODAY_STEPS, 0)
    }

    @Synchronized
    fun onHardwareCounterValue(context: Context, rawCounterValue: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val savedDay = prefs.getString(KEY_DAY, "")
        var todaySteps = prefs.getInt(KEY_TODAY_STEPS, 0)
        val lastRaw = prefs.getInt(KEY_LAST_RAW_COUNTER, -1)

        if (savedDay != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_TODAY_STEPS, 0)
                .putInt(KEY_LAST_RAW_COUNTER, rawCounterValue)
                .apply()
            return 0
        }

        val delta = when {
            lastRaw < 0 -> 0
            rawCounterValue >= lastRaw -> rawCounterValue - lastRaw
            else -> rawCounterValue
        }

        if (delta > 0) {
            todaySteps += delta
        }

        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_TODAY_STEPS, todaySteps)
            .putInt(KEY_LAST_RAW_COUNTER, rawCounterValue)
            .apply()

        return todaySteps
    }

    @Synchronized
    fun addSingleStep(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val savedDay = prefs.getString(KEY_DAY, "")
        val nextSteps = if (savedDay != today) 1 else prefs.getInt(KEY_TODAY_STEPS, 0) + 1
        prefs.edit()
            .putString(KEY_DAY, today)
            .putInt(KEY_TODAY_STEPS, nextSteps)
            .apply()
        return nextSteps
    }
}
