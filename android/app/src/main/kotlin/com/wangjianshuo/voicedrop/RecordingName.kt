package com.wangjianshuo.voicedrop

import java.text.SimpleDateFormat
import java.util.*

object RecordingName {
    private val dateFormatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    private val weekdayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val periodNames = listOf("am", "am", "am", "pm", "pm", "pm")

    fun stagingName(): String {
        val now = dateFormatter.format(Date())
        return "recording-$now.m4a"
    }

    fun promotedName(durationSec: Int, city: String? = null, district: String? = null): String {
        val now = Date()
        val ts = dateFormatter.format(now)
        val cal = Calendar.getInstance()
        cal.time = now
        val weekday = weekdayNames.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "周一" }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 0..4 -> periodNames[0]
            in 5..7 -> periodNames[1]
            in 8..10 -> periodNames[2]
            in 11..12 -> periodNames[3]
            in 13..17 -> periodNames[4]
            else -> periodNames[5]
        }
        val location = when {
            city != null && district != null -> "-$city-$district"
            city != null -> "-$city"
            else -> ""
        }
        return "VoiceDrop-$ts-${durationSec}-$weekday-$period$location.m4a"
    }

    fun stem(audioName: String): String {
        return audioName.substringBeforeLast(".m4a").substringBeforeLast(".empty")
    }

    fun sessionTs(audioName: String): String? {
        val parts = audioName.removeSuffix(".m4a").split("-")
        return if (parts.size >= 5) {
            parts.slice(1..4).joinToString("-")
        } else null
    }

    fun date(fromTimestamp: String): Date? {
        return try {
            dateFormatter.parse(fromTimestamp)
        } catch (_: Exception) { null }
    }

    fun photoKey(sessionTs: String, offset: Int): String {
        val rand = (1..46656).random().toString(36)
        return "photos/$sessionTs/$offset-$rand.jpg"
    }
}
