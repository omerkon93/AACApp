package com.kon.myaacapp

import android.content.Context
import androidx.core.graphics.scale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ImageStorageService(private val context: Context) {

    /**
     * Compresses an image from the given URI, scales it down, and saves it as WebP
     * in the app's internal storage (filesDir/images/).
     * Returns the absolute path of the saved file.
     */
    fun saveImage(uri: Uri): String? {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Changed from .jpg to .webp
        val fileName = "img_${UUID.randomUUID()}.webp"
        val outputFile = File(imagesDir, fileName)

        return try {
            val maxSize = 800f

            // 1. Open and decode the image
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Log.e("ImageStorageService", "Failed to decode bitmap from URI")
                return null
            }

            // 2. Calculate new dimensions keeping aspect ratio
            var width = originalBitmap.width.toFloat()
            var height = originalBitmap.height.toFloat()

            if (width > maxSize || height > maxSize) {
                val ratio = width / height
                if (width > height) {
                    width = maxSize
                    height = maxSize / ratio
                } else {
                    height = maxSize
                    width = maxSize * ratio
                }
            }

            // 3. Scale the bitmap
            val scaledBitmap = originalBitmap.scale(width.toInt(), height.toInt())

            // 4. Compress and save as WebP
            FileOutputStream(outputFile).use { outputStream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, outputStream)
                } else {
                    @Suppress("DEPRECATION")
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream)
                }
            }

            // 5. Free up memory immediately
            if (originalBitmap != scaledBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()

            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("ImageStorageService", "Error saving and compressing image", e)
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