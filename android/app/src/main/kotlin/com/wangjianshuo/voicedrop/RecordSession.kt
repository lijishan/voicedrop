package com.wangjianshuo.voicedrop

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun RecordSession(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceDropApp
    val library = app.library
    val auth = app.auth
    val httpClient = app.httpClient
    val promoter = remember { RecordingPromoter(context) }
    val uploader = remember { Uploader(context, httpClient, auth) }

    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var stagingFile by remember { mutableStateOf<File?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val recorder = remember { AudioRecorder(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) hasPermission = true
        else onDismiss()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                elapsed++
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isRecording) recorder.cancel()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VDTheme.Background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (isRecording) {
                    Text(
                        Formatting.formatDuration(elapsed),
                        style = VDTheme.H1.copy(fontSize = 48.sp, color = VDTheme.Red),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(com.wangjianshuo.voicedrop.R.string.recording), style = VDTheme.Body.copy(color = VDTheme.Red))
                } else {
                    Text(stringResource(com.wangjianshuo.voicedrop.R.string.tap_to_record), style = VDTheme.H2)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(com.wangjianshuo.voicedrop.R.string.recording_hint), style = VDTheme.Caption)
                }

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = {
                    if (isRecording) {
                        val file = recorder.stop()
                        isRecording = false
                        file?.let { f ->
                            val promoted = promoter.promote(f, elapsed)
                            if (promoted.exists()) {
                                Log.d("RecordSession", "recording saved: ${promoted.name}")
                            } else {
                                Log.w("RecordSession", "promote failed, keeping original: ${f.name}")
                            }
                            library.addLocalRecording(promoted.name)
                            uploader.drainPending()
                        } ?: Log.e("RecordSession", "recorder.stop() returned null")
                        onDismiss()
                        } else {
                            val file = File(context.filesDir, RecordingName.stagingName())
                            stagingFile = file
                            if (recorder.start(file)) {
                                elapsed = 0
                                isRecording = true
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) VDTheme.White else Color(0xFFCC3333)
                    ),
                    modifier = Modifier.size(64.dp).border(0.5.dp, VDTheme.Divider, CircleShape),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    if (isRecording) Icon(
                        Icons.Default.Stop, null,
                        tint = VDTheme.Red, modifier = Modifier.size(28.dp),
                    ) else Icon(
                        Icons.Outlined.Circle, null,
                        tint = VDTheme.White.copy(alpha = 0.4f), modifier = Modifier.size(44.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = {
                    if (isRecording) recorder.cancel()
                    onDismiss()
                }) {
                    Text(stringResource(com.wangjianshuo.voicedrop.R.string.cancel), style = VDTheme.Button)
                }
            }
        }
    }
}
