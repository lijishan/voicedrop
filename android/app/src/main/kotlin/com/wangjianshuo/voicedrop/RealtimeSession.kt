package com.wangjianshuo.voicedrop

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class RealtimeSession(
    private val auth: AuthStore,
    private val httpClient: HttpClient,
) {
    enum class State { idle, connecting, live, degraded }

    var onAudioDelta: ((ByteArray) -> Unit)? = null
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechStopped: (() -> Unit)? = null
    var onResponseDone: (() -> Unit)? = null
    var onStateChange: ((State) -> Unit)? = null

    var state: State = State.idle
        private set(value) { field = value; onStateChange?.invoke(value) }

    private var webSocket: WebSocket? = null
    private var generation = AtomicInteger(0)
    private var reconnectAttempt = 0
    private val maxReconnects = 6
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun connect() {
        if (webSocket != null) return
        val gen = generation.incrementAndGet()
        reconnectAttempt = 0
        state = State.connecting

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation.get() != gen) return
                this@RealtimeSession.webSocket = webSocket
                state = State.live
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation.get() != gen) return
                try {
                    val msg = Gson().fromJson(text, Map::class.java)
                    when (msg["type"]) {
                        "speech_started" -> onSpeechStarted?.invoke()
                        "speech_stopped" -> onSpeechStopped?.invoke()
                        "response_done" -> onResponseDone?.invoke()
                        "audio" -> {
                            val b64 = msg["data"] as? String ?: return
                            onAudioDelta?.invoke(Base64.decode(b64, Base64.DEFAULT))
                        }
                    }
                } catch (e: Exception) { Log.w("RealtimeSession", "parse", e) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation.get() != gen) return
                Log.w("RealtimeSession", "ws fail: ${t.message}")
                state = State.degraded
                scheduleReconnect()
            }
        }
        httpClient.webSocket("${API.AGENT_BASE}/realtime/relay?fmt=pcmu", listener)
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= maxReconnects) { state = State.idle; return }
        val delayMs = (1L shl reconnectAttempt) * 1000L
        reconnectAttempt++
        scope.launch {
            try {
                kotlinx.coroutines.delay(delayMs)
                if (generation.get() != 0) connect()
            } catch (e: Exception) {
                Log.w("RealtimeSession", "reconnect failed", e)
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        webSocket?.close(1000, null)
        webSocket = null
        state = State.idle
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    fun sendAudio(pcm: ByteArray) {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        webSocket?.send("""{"type":"audio","data":"$b64"}""")
    }
}
