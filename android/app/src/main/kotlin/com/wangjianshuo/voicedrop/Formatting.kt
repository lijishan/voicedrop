package com.wangjianshuo.voicedrop

import java.text.SimpleDateFormat
import java.util.*

object Formatting {
    private val weekdayMap = mapOf(
        "Sun" to "周日", "Mon" to "周一", "Tue" to "周二", "Wed" to "周三",
        "Thu" to "周四", "Fri" to "周五", "Sat" to "周六",
        "周日" to "周日", "周一" to "周一", "周二" to "周二", "周三" to "周三",
        "周四" to "周四", "周五" to "周五", "周六" to "周六",
    )
    private val periodMap = mapOf(
        "am" to "上午", "pm" to "下午",
        "凌晨" to "凌晨", "早晨" to "早晨", "上午" to "上午",
        "中午" to "中午", "下午" to "下午", "晚上" to "晚上",
    )

    fun formatDateTime(audioName: String): String {
        val parts = audioName.removeSuffix(".m4a").split("-")
        if (parts.size < 5) return audioName
        val dateStr = "${parts[1].take(4)}-${parts[1].drop(4).take(2)}-${parts[1].drop(6).take(2)}"
        val timeStr = "${parts[1].drop(8).take(2)}:${parts[1].drop(10).take(2)}:${parts[1].drop(12).take(2)}"
        val weekday = weekdayMap[parts.getOrNull(3)] ?: parts.getOrNull(3) ?: ""
        val period = periodMap[parts.getOrNull(4)] ?: parts.getOrNull(4) ?: ""
        return "$dateStr $timeStr $weekday$period"
    }

    fun formatRelativeTime(epochMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - epochMs
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            diff < 7 * 86400_000 -> "${diff / 86400_000} 天前"
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(epochMs))
            }
        }
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    fun formatDurationLabel(seconds: Int): String {
        if (seconds <= 0) return ""
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m${s}s" else "${s}s"
    }

    fun formatSmartDate(audioName: String): String {
        val parts = audioName.removeSuffix(".m4a").split("-")
        if (parts.size < 2 || parts[1].length < 14) return audioName
        val ts = parts[1]
        val month = ts.substring(4, 6).toIntOrNull() ?: return audioName
        val day = ts.substring(6, 8).toIntOrNull() ?: return audioName
        val hour = ts.substring(8, 10)
        val min = ts.substring(10, 12)
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val thisYear = cal.get(Calendar.YEAR)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        return when {
            dayOfYear == today -> "今天 $hour:$min"
            dayOfYear == today - 1 -> "昨天 $hour:$min"
            dayOfYear == today - 2 -> "前天 $hour:$min"
            else -> "${month}月${day}日 $hour:$min"
        }
    }

    fun formatSuanli(balanceUy: Long): String {
        val suanli = balanceUy / 23.0
        return "%.1f 算力".format(suanli)
    }
}
