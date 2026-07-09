package com.wangjianshuo.voicedrop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.text.SimpleDateFormat
import java.util.*

class RecordingNameTest {

    @Test
    fun `stem extraction`() {
        assertEquals("VoiceDrop-20260708143000-5-Wed-pm",
            RecordingName.stem("VoiceDrop-20260708143000-5-Wed-pm.m4a"))
    }

    @Test
    fun `promoted name format`() {
        val name = RecordingName.promotedName(120, null, null)
        assertTrue(name.startsWith("VoiceDrop-"))
        assertTrue(name.contains("-120-"))
        assertFalse(name.contains("周日") || name.contains("周一"))
    }

    @Test
    fun `duration from filename`() {
        val name = "VoiceDrop-20260708143000-5-Wed-pm.m4a"
        val parts = name.removeSuffix(".m4a").split("-")
        assertEquals(5, parts[2].toInt())
    }

    @Test
    fun `smart date today format`() {
        val now = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val name = "VoiceDrop-$now-5-Wed-pm.m4a"
        val result = Formatting.formatSmartDate(name)
        assertTrue(result.startsWith("今天 "))
    }

    @Test
    fun `format duration label`() {
        assertEquals("", Formatting.formatDurationLabel(0))
        assertEquals("30s", Formatting.formatDurationLabel(30))
        assertEquals("1m5s", Formatting.formatDurationLabel(65))
    }
}
