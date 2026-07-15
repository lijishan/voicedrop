
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(navController: NavController) {
    val app = LocalContext.current.applicationContext as VoiceDropApp
    val auth = app.auth
    val library = app.library
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf(auth.name ?: "") }
    var nameSaved by remember { mutableStateOf(false) }
    var styleText by remember { mutableStateOf("") }
    var showStyleEditor by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var wechatAppId by remember { mutableStateOf(auth.wechatAppId ?: "") }
    var wechatSecret by remember { mutableStateOf(auth.wechatSecret ?: "") }
    var wechatSaved by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Prompt share
    var shareStates by remember { mutableStateOf<Map<String, ShareState>>(emptyMap()) }
    var shareToggling by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var codeCopied by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }
    val shareItemID = "default"

    LaunchedEffect(Unit) {
        // try server sync, but only overwrite local cache if server returns data
        try { val n = library.fetchName(); if (n.isNotBlank()) { name = n; auth.name = n } } catch (e: Exception) { Log.w("Settings", "ignored", e) }
        try {
            val cfg = library.fetchWeChatConfig()
            if (cfg != null && cfg.appid.isNotBlank()) {
                wechatAppId = cfg.appid; wechatSecret = cfg.secret
                auth.wechatAppId = cfg.appid; auth.wechatSecret = cfg.secret
            }
        } catch (e: Exception) { Log.w("Settings", "ignored", e) }
        try {
            val styleDoc = library.fetchStyle()
            styleText = (styleDoc?.versions?.lastOrNull()?.styleText) ?: (styleDoc?.style) ?: ""
        } catch (e: Exception) { Log.w("Settings", "ignored", e) }
        try {
            val resp = library.fetchShareStates()
            shareStates = resp.byItem ?: emptyMap()
        } catch (e: Exception) { Log.w("Settings", "ignored", e) }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.wangjianshuo.voicedrop.R.string.settings), style = VDTheme.H2) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.wangjianshuo.voicedrop.R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VDTheme.Primary)
                }
            } else {
                // Name
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.name), style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; nameSaved = false },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                scope.launch {
                                    try { library.saveName(name); auth.name = name; nameSaved = true } catch (e: Exception) { Log.w("Settings", "ignored", e) }
                                }
                            }) {
                                Text(if (nameSaved) stringResource(com.wangjianshuo.voicedrop.R.string.saved) else "保存", color = if (nameSaved) VDTheme.Accent else VDTheme.Primary)
                            }
                        }
                    }
                }

                // Style
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.style), style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        Text(styleText.ifBlank { stringResource(com.wangjianshuo.voicedrop.R.string.style_not_set) }, style = VDTheme.Caption)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showStyleEditor = true }) {
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.edit_style), style = VDTheme.Button)
                        }
                    }
                }

                // Prompt Share
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    val shareState = shareStates[shareItemID]
                    val sharing = shareState?.sharing ?: false
                    val code = shareState?.code
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("提示词分享", style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (sharing) "分享中，关闭后分享码立即失效"
                                    else "开启后，任何人对 VoiceDrop 说出分享码，或打开链接，就能看到并一次性使用这条提示词",
                                    style = VDTheme.Caption,
                                )
                            }
                            if (shareToggling) {
                                CircularProgressIndicator(color = VDTheme.Accent, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                            } else {
                                Switch(
                                    checked = sharing,
                                    onCheckedChange = { on ->
                                        shareError = null
                                        shareToggling = true
                                        scope.launch {
                                            val err = library.setSharing(shareItemID, on)
                                            shareError = err
                                            shareToggling = false
                                            if (err == null) {
                                                try {
                                                    val resp = library.fetchShareStates()
                                                    shareStates = resp.byItem ?: emptyMap()
                                                } catch (e: Exception) { Log.w("Settings", "ignored", e) }
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = VDTheme.Accent),
                                )
                            }
                        }
                        if (shareError != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(shareError ?: "", style = VDTheme.Caption.copy(color = VDTheme.Accent))
                        }
                        if (sharing && code != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                code,
                                style = VDTheme.H1.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("voicedrop.cn/$code", style = VDTheme.Caption)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                                    codeCopied = true
                                    scope.launch { delay(1800); codeCopied = false }
                                }) {
                                    Text(if (codeCopied) "已复制" else "复制数字", style = VDTheme.Button)
                                }
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("link", "https://voicedrop.cn/$code"))
                                    linkCopied = true
                                    scope.launch { delay(1800); linkCopied = false }
                                }) {
                                    Text(if (linkCopied) "已复制" else "复制链接", style = VDTheme.Button)
                                }
                                TextButton(onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://voicedrop.cn/$code")
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "分享提示词"))
                                }) {
                                    Text("分享", style = VDTheme.Button)
                                }
                            }
                        }
                    }
                }

                // WeChat
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.wechat_official), style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wechatAppId,
                            onValueChange = { wechatAppId = it; wechatSaved = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("AppID") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wechatSecret,
                            onValueChange = { wechatSecret = it; wechatSaved = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("AppSecret") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                scope.launch {
                                    try { library.saveWeChatConfig(WeChatConfig(wechatAppId, wechatSecret, true)); auth.wechatAppId = wechatAppId; auth.wechatSecret = wechatSecret; wechatSaved = true } catch (e: Exception) { wechatSaved = false; android.util.Log.e("Settings", "save wechat failed: ${e.message}") }
                                }
                            }) {
                                Text(if (wechatSaved) stringResource(com.wangjianshuo.voicedrop.R.string.saved) else "保存", color = if (wechatSaved) VDTheme.Accent else VDTheme.Primary)
                            }
                        }
                    }
                }

                // Account
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.account), style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        Text("ID: ${auth.anonToken.take(16)}...", style = VDTheme.Caption)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showUsage = true }) {
                            Text(stringResource(com.wangjianshuo.voicedrop.R.string.suanli_balance), style = VDTheme.Button)
                        }
                    }
                }

                // About
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VDTheme.CardBg),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.about), style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        Text("VoiceDrop 1.1", style = VDTheme.Caption)
                        Text(stringResource(com.wangjianshuo.voicedrop.R.string.about_desc), style = VDTheme.Caption.copy(fontSize = 12.sp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VDTheme.Red),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.delete_account), color = VDTheme.White) }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showStyleEditor) {
        var editingStyle by remember { mutableStateOf(styleText) }
        AlertDialog(
            onDismissRequest = { showStyleEditor = false },
            title = { Text("编辑文风") },
            text = {
                OutlinedTextField(
                    value = editingStyle,
                    onValueChange = { editingStyle = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text(stringResource(com.wangjianshuo.voicedrop.R.string.edit_style_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { library.saveStyle(editingStyle); styleText = editingStyle } catch (e: Exception) { Log.w("Settings", "ignored", e) }
                    }
                    showStyleEditor = false
                }) { Text("保存", style = VDTheme.Button) }
            },
            dismissButton = {
                TextButton(onClick = { showStyleEditor = false }) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.cancel)) }
            }
        )
    }

    if (showUsage) {
        UsageView(onDismiss = { showUsage = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text(stringResource(com.wangjianshuo.voicedrop.R.string.delete_account_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { library.deleteAccount() } catch (e: Exception) { Log.w("Settings", "ignored", e) }
                        auth.adoptToken("")
                        navController.popBackStack()
                    }
                    showDeleteConfirm = false
                }) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.confirm_delete_account), color = VDTheme.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(com.wangjianshuo.voicedrop.R.string.cancel)) }
            }
        )
    }
}
