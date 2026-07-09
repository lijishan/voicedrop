
package com.wangjianshuo.voicedrop
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@Composable
fun UsageView(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as VoiceDropApp
    val library = app.library
    val scope = rememberCoroutineScope()

    var balance by remember { mutableStateOf<UsageBalance?>(null) }
    var ledger by remember { mutableStateOf<UsageLedger?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            balance = library.fetchUsage()
            ledger = library.fetchUsageLedger()
        } catch (e: Exception) { Log.w("UsageView", "ignored", e) }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("算力") },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VDTheme.Primary)
                }
            } else {
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VDTheme.Background),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("余额", style = VDTheme.Caption)
                            val suanli = (balance?.balanceUy ?: 0L) / 23.0
                            Text(
                                "%.1f 算力".format(suanli),
                                style = VDTheme.H1.copy(fontSize = 28.sp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "23 算力 = ¥1 · 无现金价值",
                                style = VDTheme.Caption.copy(fontSize = 11.sp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("明细", style = VDTheme.Body.copy(fontWeight = FontWeight.Medium))
                    val items = ledger?.items ?: emptyList()
                    items.take(20).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                item.reason ?: item.kind,
                                style = VDTheme.Caption,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "%.1f".format(-item.amountUy / 23.0),
                                style = VDTheme.Caption.copy(color = VDTheme.Red),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
