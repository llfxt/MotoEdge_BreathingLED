package com.moto.breathingled

import android.content.Context
import android.content.SharedPreferences

class LedPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("moto_led_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_COLOR = "dot_color"
        const val KEY_RADIUS = "dot_radius"
        const val KEY_OFFSET_X = "offset_x"
        const val KEY_OFFSET_Y = "offset_y"
        const val KEY_DURATION = "breathing_duration"
        const val KEY_PIXEL_SHIFT = "enable_pixel_shift"

        // 默认适配美版 Motorola edge 2026 的初始参数
        const val DEFAULT_COLOR = "#00FF41"
        const val DEFAULT_RADIUS = 8f
        const val DEFAULT_OFFSET_X = 20f
        const val DEFAULT_OFFSET_Y = 22f
        const val DEFAULT_DURATION = 2.2f
    }

    var dotColor: String
        get() = prefs.getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR
        set(value) = prefs.edit().putString(KEY_COLOR, value).apply()

    var dotRadiusDp: Float
        get() = prefs.getFloat(KEY_RADIUS, DEFAULT_RADIUS)
        set(value) = prefs.edit().putFloat(KEY_RADIUS, value).apply()

    var offsetXDp: Float
        get() = prefs.getFloat(KEY_OFFSET_X, DEFAULT_OFFSET_X)
        set(value) = prefs.edit().putFloat(KEY_OFFSET_X, value).apply()

    var offsetYDp: Float
        get() = prefs.getFloat(KEY_OFFSET_Y, DEFAULT_OFFSET_Y)
        set(value) = prefs.edit().putFloat(KEY_OFFSET_Y, value).apply()

    var breathingDuration: Float
        get() = prefs.getFloat(KEY_DURATION, DEFAULT_DURATION)
        set(value) = prefs.edit().putFloat(KEY_DURATION, value).apply()

    var enablePixelShift: Boolean
        get() = prefs.getBoolean(KEY_PIXEL_SHIFT, true)
        set(value) = prefs.edit().putBoolean(KEY_PIXEL_SHIFT, value).apply()
}