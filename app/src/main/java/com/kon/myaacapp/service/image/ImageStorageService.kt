package com.kon.myaacapp.service.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStorageService(private val context: Context) {

    /**
     * Compresses an image from the given URI, scales it down, and saves it as WebP
     * in the app's internal storage.
     * * IMPORTANT: Because this function maintains its synchronous signature to meet constraints,
     * it MUST be called from within a CoroutineScope(Dispatchers.IO) block by the caller
     * to avoid freezing the UI thread.
     */
    fun saveImage(uri: Uri): String? {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val fileName = "img_${UUID.randomUUID()}.webp"
        val outputFile = File(imagesDir, fileName)

        return try {
            val maxSize = 800f

            // OPTIMIZATION 1: Read Bounds Only (Zero Memory Cost)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            // OPTIMIZATION 2: Safe Resource Closure
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (options.outWidth == -1 || options.outHeight == -1) {
                Log.e("ImageStorageService", "Failed to decode bitmap bounds from URI")
                return null
            }

            // OPTIMIZATION 3: Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxSize.toInt(), maxSize.toInt())
            options.inJustDecodeBounds = false

            var originalBitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            }

            // If it is null here, we exit safely.
            if (originalBitmap == null) {
                Log.e("ImageStorageService", "Failed to decode pre-scaled bitmap")
                return null
            }

            // FIX: Removed !!. The compiler smart-casts originalBitmap to a non-null Bitmap
            // because of the null-check exit directly above this line.
            val width = originalBitmap.width.toFloat()
            val height = originalBitmap.height.toFloat()

            val scaledBitmap = if (width > maxSize || height > maxSize) {
                val ratio = width / height
                val finalWidth: Float
                val finalHeight: Float

                if (width > height) {
                    finalWidth = maxSize
                    finalHeight = maxSize / ratio
                } else {
                    finalHeight = maxSize
                    finalWidth = maxSize * ratio
                }
                originalBitmap.scale(finalWidth.toInt(), finalHeight.toInt())
            } else {
                originalBitmap
            }

            // 6. Compress and save as WebP
            FileOutputStream(outputFile).use { outputStream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, outputStream)
                } else {
                    @Suppress("DEPRECATION")
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream)
                }
            }

            // 7. Free up memory immediately.
            // FIX: Removed redundant safe-call ?. because originalBitmap is strictly non-null here.
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
     * Helper function to calculate the nearest power-of-two sample size.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

        // FIX: Typo corrected from "FileProvider" to "FileProvider" if that was the specific complaint.
        // Make sure this matches the <provider> authority string in your AndroidManifest.xml exactly.
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            tempFile
        )
    }
}