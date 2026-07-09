package com.wangjianshuo.voicedrop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*

@Composable
fun PushToTalkBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onFinish: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val auth = LocalAuthStore.current
    val httpClient = LocalHttpClient.current
    val voiceEdit = remember { VoiceEdit(context, auth, httpClient) }

    DisposableEffect(Unit) { onDispose { voiceEdit.stop() } }

    var isPressing by remember { mutableStateOf(false) }
    var transcription by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(if (isPressing) VDTheme.Primary.copy(alpha = 0.1f) else VDTheme.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (isPressing) {
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .background(VDTheme.Primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    transcription.ifBlank { "说话中..." },
                    style = VDTheme.Body,
                    color = VDTheme.Primary,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .background(VDTheme.Divider, RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressing = true
                                transcription = ""
                                voiceEdit.start(
                                    onTranscript = { t -> transcription = t; onTextChanged(t) },
                                    onFinalText = { final ->
                                        isPressing = false
                                        if (final.isNotBlank()) onFinish(final) else onCancel()
                                    }
                                )
                                tryAwaitRelease()
                                voiceEdit.stop()
                                isPressing = false
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Mic, null, tint = VDTheme.TextHint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = text.ifBlank { stringResource(com.wangjianshuo.voicedrop.R.string.hold_speak) },
                    style = VDTheme.Body,
                    color = if (text.isBlank()) VDTheme.TextHint else VDTheme.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
