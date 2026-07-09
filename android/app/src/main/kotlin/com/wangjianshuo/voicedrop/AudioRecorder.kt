
package com.wangjianshuo.voicedrop
import android.util.Log
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var isRecording = false
        private set
    var currentFile: File? = null
        private set
    var elapsedSeconds = 0
        private set

    @Suppress("DEPRECATION")
    fun start(outputFile: File): Boolean {
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            currentFile = outputFile
            isRecording = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            currentFile
        } finally {
            recorder = null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (e: Exception) { Log.w("AudioRecorder", "ignored", e) }
        recorder?.release()
        recorder = null
        isRecording = false
        currentFile?.delete()
        currentFile = null
    }

    fun getMaxAmplitude(): Int {
        return recorder?.maxAmplitude ?: 0
    }
}
