package com.wangjianshuo.voicedrop

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

class EngineRecorder {
    var onPCM: ((ByteArray) -> Unit)? = null
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    val isRec: Boolean get() = isRecording.get()
    private var engineThread: Thread? = null

    private val SAMPLE_RATE = 8000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val FORMAT = AudioFormat.ENCODING_PCM_16BIT

    fun start(): Boolean {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)
        return try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, bufSize * 2)
            audioRecord?.startRecording()
            isRecording.set(true)
            engineThread = Thread {
                val buffer = ByteArray(bufSize)
                while (isRecording.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, bufSize) ?: -1
                    if (bytesRead > 0) onPCM?.invoke(buffer.copyOf(bytesRead))
                }
            }
            engineThread?.start()
            true
        } catch (e: Exception) { false }
    }

    fun stop() {
        isRecording.set(false)
        engineThread?.join(1000)
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }
}
