package com.wangjianshuo.voicedrop

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable
fun FollowupCard(
    articleIndex: Int,
    questions: List<FollowupQuestion>,
    onAnswer: (FollowupQuestion) -> Unit,
    onSkip: (FollowupQuestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val displayQuestions = questions.filter { it.articleIndex == articleIndex }
    if (displayQuestions.isEmpty()) return

    val pending = displayQuestions.filter { it.status == QuestionStatus.PENDING }
    val unansweredCount = pending.size

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = VDTheme.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(com.wangjianshuo.voicedrop.R.string.followup_title) + " ($unansweredCount)", style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(12.dp))
            pending.forEach { question ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(question.text, style = VDTheme.Body)
                    }
                    Row {
                        TextButton(onClick = { onSkip(question) }) {
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.skip), style = VDTheme.Caption)
                        }
                        TextButton(onClick = { onAnswer(question) }) {
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.answer), style = VDTheme.Button)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FollowupBadge(count: Int, onClick: () -> Unit) {
    if (count <= 0) return
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).clip(CircleShape).background(VDTheme.CardBg),
    ) {
        Box {
            Icon(Icons.Default.Star, stringResource(com.wangjianshuo.voicedrop.R.string.followup_title), tint = VDTheme.Primary, modifier = Modifier.size(24.dp))
            if (count > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(VDTheme.Red),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$count", color = VDTheme.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
