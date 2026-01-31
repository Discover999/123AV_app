package com.android123av.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtils {

    private const val LIGHT_HAPTIC_DURATION = 10L
    private const val MEDIUM_HAPTIC_DURATION = 20L
    private const val HEAVY_HAPTIC_DURATION = 40L

    fun vibrateLight(context: Context) {
        vibrate(context, LIGHT_HAPTIC_DURATION)
    }

    fun vibrateMedium(context: Context) {
        vibrate(context, MEDIUM_HAPTIC_DURATION)
    }

    fun vibrateHeavy(context: Context) {
        vibrate(context, HEAVY_HAPTIC_DURATION)
    }

    fun vibrateClick(context: Context) {
        vibrate(context, LIGHT_HAPTIC_DURATION)
    }

    fun vibrateLongPress(context: Context) {
        vibrate(context, MEDIUM_HAPTIC_DURATION)
    }

    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                it.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    fun hasVibrator(context: Context): Boolean {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator?.hasVibrator() == true
    }
}
