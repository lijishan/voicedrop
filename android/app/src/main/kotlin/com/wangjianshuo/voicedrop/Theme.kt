package com.wangjianshuo.voicedrop

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

object VDTheme {
    val Primary = Color(0xFFC75B3A)
    val PrimaryLight = Color(0xFFE8A890)
    val Background = Color(0xFFFAF6EF)
    val CardBg = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF2D221C)
    val TextSecondary = Color(0xFF8B7E74)
    val TextHint = Color(0xFFC2B8A8)
    val Divider = Color(0xFFE8E0D5)
    val Red = Color(0xFFCC3333)
    val Accent = Color(0xFF5B8C5A)
    val White = Color(0xFFFFFFFF)

    val H1 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    val H2 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, color = TextPrimary)
    val Caption = TextStyle(fontSize = 13.sp, color = TextSecondary)
    val Button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Primary)
    val Title1 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
}
