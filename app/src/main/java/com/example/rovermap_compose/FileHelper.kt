package com.example.rovermap_compose

import android.content.Context
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// we can't provide the rovermap framework with a content path so we need to
// copy the asset to the app's external directory and provide that path instead
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun getFilePath(
    context: Context,
    documentIntentResolver: DocumentIntentResolver,
    isLoading: (loading: Boolean) -> Unit,
    onProgressUpdate: (progress: Double) -> Unit,
): File? {
    documentIntentResolver.getFile()?.let {  fileUri ->
        return withContext(Dispatchers.IO) {
            val externalFilesDir = context.getExternalFilesDir("cache")

            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                var totalSize = 0L
                var fileName = ""

                context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val fileNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    totalSize = cursor.getLong(sizeIndex)
                    fileName = cursor.getString(fileNameIndex)
                }

                val file = File(
                    externalFilesDir,
                    fileName.ifEmpty { "default" }
                )

                if (!file.exists() || file.length() != totalSize) {
                    file.outputStream().use { outputStream ->
                        isLoading(true)

                        var bytesCopied = 0.0
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            bytes = inputStream.read(buffer)

                            onProgressUpdate(bytesCopied / totalSize * 100.0)
                        }

                        isLoading(false)
                    }
                }

                return@withContext file
            }
        }
    }

    return null
}

// we can't provide the rovermap framework with a asset path so we need to
// copy the asset to the app's external directory and provide that path instead
fun copyAsset(context: Context, fileName: String, destFile: File) {
    context.assets.open(fileName).use { inStr ->
        FileOutputStream(destFile).use { outStr ->
            inStr.copyTo(outStr, 4096)
        }
    }
}