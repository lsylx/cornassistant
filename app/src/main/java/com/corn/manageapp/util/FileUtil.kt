package com.corn.manageapp.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object FileUtil {

    fun saveJsonToSdcard(context: Context, json: String): String? {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "hardware_$time.json"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ✅ Android 10+ （MediaStore）
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/DCIMDebug")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                "/sdcard/Download/DCIMDebug/$fileName"

            } else {
                // ✅ Android 9 及以下（直接写 /sdcard）
                val dir = File(Environment.getExternalStorageDirectory(), "DCIMDebug")
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(json.toByteArray()) }

                file.absolutePath
            }

        } catch (e: Exception) {
            null
        }
    }
}
