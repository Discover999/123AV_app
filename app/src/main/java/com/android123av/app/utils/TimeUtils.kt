package com.android123av.app.utils

/**
 * 时间格式化工具类
 * 提供视频时长、时间戳等格式化功能
 */
object TimeUtils {
    /**
     * 将毫秒时间格式化为 HH:MM:SS 或 MM:SS 格式
     * @param timeMs 毫秒数
     * @return 格式化后的时间字符串
     */
    fun formatTime(timeMs: Long): String {
        if (timeMs < 0) return "00:00"
        
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    /**
     * 将秒数格式化为 HH:MM:SS 或 MM:SS 格式
     * @param seconds 秒数
     * @return 格式化后的时间字符串
     */
    fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "00:00"
        
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
