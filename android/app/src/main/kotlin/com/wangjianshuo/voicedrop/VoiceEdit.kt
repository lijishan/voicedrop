
package com.wangjianshuo.voicedrop
import android.util.Log
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

class VoiceEdit(
    private val context: Context,
    private val auth: AuthStore,
    private val httpClient: HttpClient,
) {
    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private val isRecording = AtomicBoolean(false)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var seq = 0

    private var onTranscript: ((String) -> Unit)? = null
    private var onFinalText: ((String) -> Unit)? = null
    private var lastTranscript = ""

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun start(onTranscript: (String) -> Unit, onFinalText: (String) -> Unit) {
        if (!hasPermission()) return
        this.onTranscript = onTranscript
        this.onFinalText = onFinalText
        seq = 0
        isRecording.set(true)
        connectAndStream()
    }

    fun stop() {
        isRecording.set(false)
        webSocket?.close(1000, "user_stop")
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun connectAndStream() {
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@VoiceEdit.webSocket = webSocket
                sendStartFrame(webSocket)
                startRecording(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val gson = com.google.gson.Gson()
                    val msg = gson.fromJson(text, Map::class.java)
                    val transcriptText = msg["text"] as? String ?: return
                    lastTranscript = transcriptText
                    onTranscript?.invoke(transcriptText)
                } catch (e: Exception) { Log.w("VoiceEdit", "ignored", e) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isRecording.set(false)
                val finalText = "rec failed: ${t.message}"
                onFinalText?.invoke(finalText)
            }
        }
        httpClient.webSocket(API.wsAsr(), listener)
    }

    private fun sendStartFrame(ws: WebSocket) {
        val startMsg = mapOf(
            "type" to "start",
            "data" to mapOf(
                "format" to "pcm",
                "rate" to SAMPLE_RATE,
                "bits" to 16,
                "channel" to 1,
                "language" to "zh-CN",
                "version" to "1.0",
            )
        )
        ws.send(com.google.gson.Gson().toJson(startMsg))
    }

    private fun startRecording(ws: WebSocket) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            ).also { it.startRecording() }

            scope.launch {
                val buffer = ByteArray(bufferSize)
                val totalBuf = mutableListOf<Byte>()
                while (isRecording.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: -1
                    if (bytesRead > 0) {
                        totalBuf.addAll(buffer.take(bytesRead))
                        if (totalBuf.size >= bufferSize * 4) {
                            val frame = buildAudioFrame(totalBuf.toByteArray(), seq++)
                            ws.send(ByteString.of(*frame))
                            totalBuf.clear()
                        }
                    }
                }
                val finalText = lastTranscript
                onFinalText?.invoke(finalText)
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording.set(false)
        }
    }

    private fun buildAudioFrame(pcmData: ByteArray, seq: Int): ByteArray {
        val gzipStream = ByteArrayOutputStream()
        GZIPOutputStream(gzipStream).use { gz ->
            gz.write(0x01)
            gz.write(pcmData)
            gz.finish()
        }
        val compressed = gzipStream.toByteArray()
        val header = ByteArray(4)
        val size = compressed.size
        header[0] = ((size shr 24) and 0xFF).toByte()
        header[1] = ((size shr 16) and 0xFF).toByte()
        header[2] = ((size shr 8) and 0xFF).toByte()
        header[3] = (size and 0xFF).toByte()
        return header + compressed
    }
}
