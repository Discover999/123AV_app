package com.android123av.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 触觉反馈工具类
 * 提供不同强度的振动反馈，用于按钮点击、长按等操作
 */
object HapticUtils {

    private const val LIGHT_HAPTIC_DURATION = 10L
    private const val MEDIUM_HAPTIC_DURATION = 20L
    private const val HEAVY_HAPTIC_DURATION = 40L

    /**
     * 轻度振动反馈
     * @param context 上下文
     */
    fun vibrateLight(context: Context) {
        vibrate(context, LIGHT_HAPTIC_DURATION)
    }

    /**
     * 中度振动反馈
     * @param context 上下文
     */
    fun vibrateMedium(context: Context) {
        vibrate(context, MEDIUM_HAPTIC_DURATION)
    }

    /**
     * 重度振动反馈
     * @param context 上下文
     */
    fun vibrateHeavy(context: Context) {
        vibrate(context, HEAVY_HAPTIC_DURATION)
    }

    /**
     * 点击振动反馈
     * @param context 上下文
     */
    fun vibrateClick(context: Context) {
        vibrate(context, LIGHT_HAPTIC_DURATION)
    }

    /**
     * 长按振动反馈
     * @param context 上下文
     */
    fun vibrateLongPress(context: Context) {
        vibrate(context, MEDIUM_HAPTIC_DURATION)
    }

    /**
     * 执行振动
     * @param context 上下文
     * @param durationMs 振动时长（毫秒）
     */
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

    /**
     * 检查设备是否支持振动
     * @param context 上下文
     * @return 是否支持振动
     */
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
