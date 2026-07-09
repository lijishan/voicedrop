package com.wangjianshuo.voicedrop

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class Uploader(
    private val context: Context,
    private val httpClient: HttpClient,
    private val auth: AuthStore,
) {
    private var isDraining = false
    private var drainAgain = false

    fun drainPending() {
        if (isDraining) {
            drainAgain = true
            return
        }
        isDraining = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) {
                    drainAgain = false
                    // promote orphan staging files first
                    val promoter = RecordingPromoter(context)
                    val stagingFiles = context.filesDir.listFiles()
                        ?.filter { it.name.startsWith("recording-") && it.name.endsWith(".m4a") }
                        ?: emptyList()
                    for (f in stagingFiles) {
                        try { promoter.promote(f, 0) } catch (e: Exception) { Log.w("Uploader", "ignored", e) }
                    }

                    val pending = pendingFiles()
                    for (file in pending) {
                        try {
                            uploadOne(file)
                        } catch (e: Exception) {
                            Log.w("Uploader", "failed: ${file.name}: ${e.message}")
                        }
                    }
                    if (!drainAgain) break
                }
            } finally {
                isDraining = false
            }
        }
    }

    suspend fun uploadOne(file: File) {
        val key = file.name
        val body = file.readBytes()
        val response = httpClient.put("${API.FILES_BASE}/upload/$key", body)
        if (response.isSuccessful) {
            file.delete()
            Log.i("Uploader", "ok: ${file.name}")
        } else if (response.code in 400..499) {
            Log.e("Uploader", "err ${response.code}: ${file.name}")
        } else {
            Log.w("Uploader", "retry ${response.code}: ${file.name}")
        }
    }

    private fun pendingFiles(): List<File> {
        val dir = context.filesDir
        return dir.listFiles()?.filter {
            it.name.startsWith("VoiceDrop-") && it.name.endsWith(".m4a")
        }?.sortedBy { it.lastModified() } ?: emptyList()
    }
}
