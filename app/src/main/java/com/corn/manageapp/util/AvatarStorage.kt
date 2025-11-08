package com.corn.manageapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AvatarStorage {

    private const val DIRECTORY = "avatars"

    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, DIRECTORY)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                file.absolutePath
            }.getOrNull()
        }
    }

    suspend fun copyFromUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, DIRECTORY)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching null
                file.absolutePath
            }.getOrNull()
        }
    }

    suspend fun loadBitmap(context: Context, path: String?): Bitmap? {
        return withContext(Dispatchers.IO) {
            if (path.isNullOrEmpty()) return@withContext null
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }

    suspend fun deleteAvatar(path: String?) {
        if (path.isNullOrEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}
