package com.kon.myaacapp

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

class ImageStorageService(private val context: Context) {

    /**
     * Copies an image from the given URI to the app's internal storage (filesDir/images/).
     * Returns the absolute path of the saved file.
     */
    fun saveImage(uri: Uri): String? {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val fileName = "img_${UUID.randomUUID()}.jpg"
        val outputFile = File(imagesDir, fileName)

        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("ImageStorageService", "Error saving image", e)
            null
        }
    }

    /**
     * Generates a temporary URI for storing high-resolution camera output or crop results.
     */
    fun getTempUri(): Uri {
        val tempDir = File(context.cacheDir, "temp_images")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val tempFile = File(tempDir, "temp_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
}