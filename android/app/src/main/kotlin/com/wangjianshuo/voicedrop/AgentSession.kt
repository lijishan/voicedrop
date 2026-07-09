package com.wangjianshuo.voicedrop

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*

class AgentSession(
    private val stem: String,
    private val auth: AuthStore,
    private val httpClient: HttpClient,
) {
    private var webSocket: WebSocket? = null
    var isConnected by mutableStateOf(false)
    var isProcessing by mutableStateOf(false)
    var replyText by mutableStateOf<String?>(null)
    var updatedDoc by mutableStateOf<ArticleDoc?>(null)

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = mutableListOf<EditRequest>()
    private var currentId: String? = null

    data class EditRequest(val id: String, val text: String, val articleIndex: Int = 0)

    fun connect() {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@AgentSession.webSocket = webSocket
                isConnected = true
                drainQueue()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("AgentSession", "ws failure: ${t.message}")
                isConnected = false
                isProcessing = false
            }
        }
        httpClient.webSocket(API.wsEdit(stem), listener)
    }

    fun enqueue(text: String) {
        val id = "android_${System.currentTimeMillis()}_${(1000..9999).random()}"
        queue.add(EditRequest(id, text))
        if (isConnected && !isProcessing) drainQueue()
    }

    private fun drainQueue() {
        if (queue.isEmpty() || isProcessing) return
        val next = queue.removeAt(0)
        currentId = next.id
        isProcessing = true
        val msg = mapOf(
            "type" to "instruct",
            "id" to next.id,
            "text" to next.text,
            "articleIndex" to next.articleIndex.toString(),
        )
        webSocket?.send(gson.toJson(msg))
    }

    private fun handleMessage(text: String) {
        try {
            val msg = gson.fromJson(text, WSMessage::class.java)
            when (msg.type) {
                "updated" -> {
                    updatedDoc = msg.doc
                    replyText = "修改完成"
                    isProcessing = false
                    drainQueue()
                }
                "reply" -> {
                    replyText = msg.text
                    isProcessing = false
                    drainQueue()
                }
                "error" -> {
                    replyText = msg.message ?: "出错了"
                    isProcessing = false
                    drainQueue()
                }
            }
        } catch (e: Exception) {
            Log.w("AgentSession", "parse error: ${e.message}")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        scope.cancel()
    }

}
