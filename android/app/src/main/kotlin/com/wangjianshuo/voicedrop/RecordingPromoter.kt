package com.wangjianshuo.voicedrop

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RecordingPromoter(private val context: Context) {
    fun promote(stagingFile: File, durationSec: Int, city: String? = null, district: String? = null): File {
        val newName = RecordingName.promotedName(durationSec, city, district)
        val destFile = File(context.filesDir, newName)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Files.move(stagingFile.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } else {
                stagingFile.copyTo(destFile, overwrite = true)
                stagingFile.delete()
            }
            Log.d("RecordingPromoter", "promoted: ${stagingFile.name} -> $newName")
        } catch (e: Exception) {
            Log.e("RecordingPromoter", "move failed, trying copy: ${e.message}")
            try {
                stagingFile.copyTo(destFile, overwrite = true)
                stagingFile.delete()
            } catch (e2: Exception) {
                Log.e("RecordingPromoter", "copy also failed: ${e2.message}")
            }
        }
        return destFile
    }

    fun filesDir(): File = context.filesDir
}
