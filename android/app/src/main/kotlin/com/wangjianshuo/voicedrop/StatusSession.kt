package com.wangjianshuo.voicedrop

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*

class StatusSession(
    private val auth: AuthStore,
    private val httpClient: HttpClient,
    private val library: LibraryStore,
) {
    private var webSocket: WebSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("StatusSession", "connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("StatusSession", "failed: ${t.message}, retrying in 5s")
                scope.launch {
                    delay(5000)
                    connect()
                }
            }
        }
        webSocket = httpClient.webSocket(API.wsStatus(), listener)
    }

    private fun handleMessage(text: String) {
        try {
            val msg = com.google.gson.Gson().fromJson(text, WSMessage::class.java)
            when (msg.type) {
                "status_update" -> {
                    val stem = msg.stem ?: return
                    val status = msg.status ?: return
                    when (status) {
                        "asr", "mining" -> {
                            val phase = if (status == "asr") MiningPhase.asr else MiningPhase.mining
                            library.markPhase(stem, phase)
                        }
                        "ready", "empty" -> library.markDone(stem, status)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("StatusSession", "parse error: ${e.message}")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        scope.cancel()
    }
}
