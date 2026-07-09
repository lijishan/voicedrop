package com.wangjianshuo.voicedrop

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeInterviewer(
    private val auth: AuthStore,
    private val httpClient: HttpClient,
) {
    val engine = EngineRecorder()
    val session = RealtimeSession(auth, httpClient)

    private var _interviewActive = false
    val interviewActive: Boolean get() = _interviewActive
    private val aiSpeaking = AtomicBoolean(false)
    private val uplinkMuted = AtomicBoolean(false)

    private var audioTrack: AudioTrack? = null
    private var turnCount = 0

    init {
        session.onAudioDelta = { pcm -> playAIAudio(pcm) }
        session.onSpeechStarted = { aiSpeaking.set(true); uplinkMuted.set(false) }
        session.onSpeechStopped = { aiSpeaking.set(false) }
        session.onResponseDone = {
            aiSpeaking.set(false)
            Thread { Thread.sleep(300); uplinkMuted.set(false) }.start()
        }
        session.onStateChange = { Log.d("RealtimeInterviewer", "state: $it") }

            engine.onPCM = { pcm ->
                if (_interviewActive && !aiSpeaking.get() && !uplinkMuted.get()) {
                    session.sendAudio(pcm)
                }
            }
    }

    fun toggleInterview() {
            if (!_interviewActive) {
                _interviewActive = true
            turnCount++
            session.connect()
            if (!engine.isRec) engine.start()
            Log.d("RealtimeInterviewer", "interview on")
            } else {
                _interviewActive = false
            uplinkMuted.set(true)
            session.disconnect()
            Log.d("RealtimeInterviewer", "interview off")
        }
    }

    private fun playAIAudio(pcmBytes: ByteArray) {
        try {
            if (audioTrack == null) {
                val bufSize = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(bufSize).build()
                audioTrack?.play()
            }
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) { Log.w("RealtimeInterviewer", "play err", e) }
    }

    fun stop() {
        _interviewActive = false
        session.disconnect()
        engine.stop()
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }
}
