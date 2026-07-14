package com.wangjianshuo.voicedrop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailView(stem: String, navController: NavController) {
    val library = LocalLibraryStore.current
    val auth = LocalAuthStore.current
    val httpClient = LocalHttpClient.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val recording = library.recordings.find { it.stem == stem }
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf<ArticleDoc?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showPushToTalk by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var agentSession by remember { mutableStateOf<AgentSession?>(null) }
    var mineTriggered by remember { mutableStateOf(false) }
    var mineMsg by remember { mutableStateOf("") }
    var showShareMenu by remember { mutableStateOf(false) }
    var shareResultMsg by remember { mutableStateOf("") }
    var showShareResult by remember { mutableStateOf(false) }

    // Long press menu state
    var menuVisible by remember { mutableStateOf(false) }
    var menuAnchorY by remember { mutableFloatStateOf(0f) }
    var menuAnchorX by remember { mutableFloatStateOf(0f) }
    var menuAnchorWidth by remember { mutableFloatStateOf(0f) }
    var menuNodes by remember { mutableStateOf<List<MenuNode>>(emptyList()) }

    LaunchedEffect(stem) {
        try { doc = library.fetchArticleDoc(stem) } catch (e: Exception) { Log.w("RecordingDetail", "ignored", e) }
        isLoading = false
    }

    LaunchedEffect(doc) {
        if (doc == null) {
            var retries = 0
            while (retries < 5) {
                delay(8_000)
                try { doc = library.fetchArticleDoc(stem); if (doc != null) break } catch (e: Exception) { Log.w("RecordingDetail", "ignored", e) }
                retries++
            }
        }
    }

    DisposableEffect(stem) {
        onDispose { agentSession?.disconnect() }
    }

    val title = doc?.articles?.firstOrNull()?.title ?: doc?.title ?: recording?.rowTitle ?: "录音"
    val dateInfo = recording?.dateTimeLabel ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.wangjianshuo.voicedrop.R.string.back))
                    }
                },
                actions = {
                    if (recording?.hasArticles == true) {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Default.Share, stringResource(com.wangjianshuo.voicedrop.R.string.share))
                        }
                    }
                }
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VDTheme.Primary)
                    }
                }
                doc != null -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(title, style = VDTheme.H1, modifier = Modifier.padding(bottom = 4.dp))
                        if (dateInfo.isNotBlank()) {
                            val author = auth.name
                            val meta = if (author != null && author.isNotBlank()) "$dateInfo · $author" else dateInfo
                            Text(meta, style = VDTheme.Caption.copy(fontSize = 12.sp), modifier = Modifier.padding(bottom = 16.dp))
                        }
                        val d = doc
                        val article = d?.articles?.firstOrNull()
                        val body = article?.body ?: d?.body ?: ""
                        if (d != null && article != null) ArticleBodyView(
                            body = article.body,
                            ownerScope = auth.scope,
                            onTextLongPress = { line, text, offset ->
                                val quote = text.take(15).replace("\"", "'")
                                val subs = mapOf("LINE" to line.toString(), "QUOTE" to quote)
                                menuNodes = MenuConfig.textMenu.map { MenuConfig.fillNode(it, subs) } +
                                    MenuNode(id = "copy", label = "拷贝", instruction = "COPY:$text")
                                menuAnchorY = offset.y; menuAnchorX = offset.x
                                menuAnchorWidth = 200f; menuVisible = true
                            },
                            onImageLongPress = { relKey, offset ->
                                val subs = mapOf("KEY" to relKey)
                                menuNodes = MenuConfig.imageMenu.map { MenuConfig.fillNode(it, subs) }
                                menuAnchorY = offset.y; menuAnchorX = offset.x
                                menuAnchorWidth = 200f; menuVisible = true
                            },
                        )
                        else if (d?.body != null) ArticleBodyView(
                            body = d.body,
                            ownerScope = auth.scope,
                            onTextLongPress = { line, text, offset ->
                                val quote = text.take(15).replace("\"", "'")
                                val subs = mapOf("LINE" to line.toString(), "QUOTE" to quote)
                                menuNodes = MenuConfig.textMenu.map { MenuConfig.fillNode(it, subs) } +
                                    MenuNode(id = "copy", label = "拷贝", instruction = "COPY:$text")
                                menuAnchorY = offset.y; menuAnchorX = offset.x
                                menuAnchorWidth = 200f; menuVisible = true
                            },
                            onImageLongPress = { relKey, offset ->
                                val subs = mapOf("KEY" to relKey)
                                menuNodes = MenuConfig.imageMenu.map { MenuConfig.fillNode(it, subs) }
                                menuAnchorY = offset.y; menuAnchorX = offset.x
                                menuAnchorWidth = 200f; menuVisible = true
                            },
                        )
                    }
                }
                recording?.isEmpty == true -> { EmptyStateView() }
                recording != null -> {
                    PendingStateView(recording = recording,
                        onTriggerMine = { msg -> scope.launch {
                            try { mineTriggered = true; mineMsg = msg; library.triggerMine(stem) }
                            catch (e: Exception) { mineMsg = "触发失败: ${e.message}" }
                        }},
                        mineTriggered = mineTriggered, mineMsg = mineMsg,
                    )
                }
                else -> { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("未知录音") } }
            }

            if (recording?.hasArticles == true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    if (showPushToTalk) {
                        PushToTalkBar(text = editText, onTextChanged = { editText = it },
                            onFinish = { text ->
                                if (text.isNotBlank()) {
                                    val session = agentSession ?: AgentSession(stem, auth, httpClient)
                                    agentSession = session
                                    if (!session.isConnected) session.connect()
                                    session.enqueue(text)
                                }
                                showPushToTalk = false; editText = ""
                            },
                            onCancel = { showPushToTalk = false; editText = "" }
                        )
                    } else {
                        Surface(onClick = { showPushToTalk = true }, shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF2D221C), shadowElevation = 4.dp,
                            modifier = Modifier.padding(bottom = 24.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(com.wangjianshuo.voicedrop.R.string.hold_to_edit), color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Share menu dialog
    if (showShareMenu) {
        AlertDialog(
            onDismissRequest = { showShareMenu = false },
            title = { Text("分享") },
            text = {
                Column {
                    TextButton(onClick = {
                        showShareMenu = false
                        scope.launch {
                            try {
                                val url = library.shareArticle("articles/$stem.json")
                                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                                context.startActivity(Intent.createChooser(intent, "分享文章"))
                            } catch (e: Exception) {
                                Log.w("RecordingDetail", "share link failed", e)
                                shareResultMsg = "分享失败: ${e.message}"
                                showShareResult = true
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, null, tint = VDTheme.TextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.share_link))
                        }
                    }
                    val shareOkMsg = stringResource(com.wangjianshuo.voicedrop.R.string.share_success)
                    TextButton(onClick = {
                        showShareMenu = false
                        scope.launch {
                            try {
                                library.shareArticle("articles/$stem.json")
                                val communityStore = CommunityStore(httpClient, auth)
                                communityStore.share("articles/$stem.json")
                                shareResultMsg = shareOkMsg
                            } catch (e: Exception) { shareResultMsg = "分享失败: ${e.message}" }
                            showShareResult = true
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, null, tint = VDTheme.TextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.share_to_community))
                        }
                    }
                    TextButton(onClick = {
                        showShareMenu = false
                        scope.launch {
                            try {
                                val result = library.postWeChat("articles/$stem.json")
                                shareResultMsg = if (result.ok) "已发布到公众号草稿箱" else "发布失败: ${result.errmsg}"
                            } catch (e: Exception) { shareResultMsg = "发布失敗: ${e.message}" }
                            showShareResult = true
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, null, tint = VDTheme.TextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("发布到公众号草稿箱")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShareMenu = false }) { Text("取消") } }
        )
    }

    if (showShareResult) {
        AlertDialog(
            onDismissRequest = { showShareResult = false },
            title = { Text("结果") },
            text = { Text(shareResultMsg) },
            confirmButton = { TextButton(onClick = { showShareResult = false }) { Text("确定") } }
        )
    }

    if (menuVisible) {
        LongpressMenuOverlay(
            anchorY = menuAnchorY,
            anchorX = menuAnchorX,
            anchorWidth = menuAnchorWidth,
            config = menuNodes,
            onPick = { instruction ->
                menuVisible = false
                if (instruction.startsWith("COPY:")) {
                    val text = instruction.removePrefix("COPY:")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                } else {
                    val session = agentSession ?: AgentSession(stem, auth, httpClient)
                    agentSession = session
                    if (!session.isConnected) session.connect()
                    session.enqueue(instruction)
                }
            },
            onDismiss = { menuVisible = false },
        )
    }
}


@Composable
private fun PendingStateView(
    recording: Recording,
    onTriggerMine: (String) -> Unit = { _ -> },
    mineTriggered: Boolean = false,
    mineMsg: String = "",
) {
    val (statusText, statusColor) = when {
        recording.blockReason == BlockReason.noCredit -> stringResource(com.wangjianshuo.voicedrop.R.string.status_no_credit) to VDTheme.Red
        recording.blockReason == BlockReason.tooLong -> stringResource(com.wangjianshuo.voicedrop.R.string.status_too_long) to VDTheme.Red
        !recording.uploaded -> stringResource(com.wangjianshuo.voicedrop.R.string.status_uploading) to VDTheme.TextSecondary
        recording.phase == MiningPhase.asr -> stringResource(com.wangjianshuo.voicedrop.R.string.status_asr_long) to VDTheme.Primary
        recording.phase == MiningPhase.mining -> stringResource(com.wangjianshuo.voicedrop.R.string.status_mining_long) to VDTheme.Primary
        else -> stringResource(com.wangjianshuo.voicedrop.R.string.status_pending) to VDTheme.TextSecondary
    }
    val showSpinner = !recording.uploaded || recording.phase != null || mineTriggered
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) { CircularProgressIndicator(color = VDTheme.Primary, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(24.dp)) }
            else { Icon(Icons.Default.Mic, null, tint = VDTheme.TextHint, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(16.dp)) }
            Text(statusText, style = VDTheme.H2.copy(color = statusColor))
            Spacer(Modifier.height(8.dp))
            Text(recording.dateTimeLabel, style = VDTheme.Caption, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    recording.isEmpty -> stringResource(com.wangjianshuo.voicedrop.R.string.status_empty_desc)
                    !recording.uploaded -> stringResource(com.wangjianshuo.voicedrop.R.string.status_uploading_desc)
                    mineTriggered -> stringResource(com.wangjianshuo.voicedrop.R.string.status_triggered_desc)
                    else -> stringResource(com.wangjianshuo.voicedrop.R.string.status_pending_desc)
                },
                style = VDTheme.Caption.copy(fontSize = 13.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (!mineTriggered && recording.phase == null && !recording.isEmpty && recording.uploaded) {
                Spacer(Modifier.height(24.dp))
                val msg = stringResource(com.wangjianshuo.voicedrop.R.string.status_triggered_desc)
                Button(onClick = { onTriggerMine(msg) }, shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VDTheme.Primary),
                ) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.status_mine_now), color = Color.White) }
            }
            if (mineMsg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(mineMsg, style = VDTheme.Caption.copy(color = VDTheme.Accent)) }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Forum, null, tint = VDTheme.TextHint, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(com.wangjianshuo.voicedrop.R.string.status_empty), style = VDTheme.H2.copy(color = VDTheme.TextSecondary))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(com.wangjianshuo.voicedrop.R.string.status_empty_detail), style = VDTheme.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
