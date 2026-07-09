package com.wangjianshuo.voicedrop

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareIntakeScreen(navController: NavController, intent: Intent?) {
    val context = LocalContext.current
    val auth = LocalAuthStore.current
    val httpClient = LocalHttpClient.current

    var status by remember { mutableStateOf("处理分享内容...") }
    var isDone by remember { mutableStateOf(false) }

    LaunchedEffect(intent) {
        try {
            when (intent?.action) {
                Intent.ACTION_SEND -> {
                    when {
                        intent.type?.startsWith("text/") == true -> {
                            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                            if (!text.isNullOrBlank()) {
                                httpClient.post<Unit>(
                                    "${API.FILES_BASE}/style/collect",
                                    mapOf("type" to "text", "title" to "Android 分享", "text" to text, "source" to "android-share")
                                )
                                status = "已保存到风格数据集"
                            }
                        }
                        intent.type?.startsWith("image/") == true -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            status = "图片已接收: $uri"
                        }
                        intent.type?.startsWith("audio/") == true -> {
                            status = "音频已接收"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            status = "处理失败: ${e.message}"
        }
        isDone = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VoiceDrop 接收") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!isDone) {
                CircularProgressIndicator(color = VDTheme.Primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(status, style = VDTheme.Body)
            if (isDone) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("完成")
                }
            }
        }
    }
}
