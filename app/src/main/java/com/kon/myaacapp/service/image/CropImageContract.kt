package com.kon.myaacapp.service.image

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView

/**
 * A custom [androidx.activity.result.contract.ActivityResultContract] to start an activity that allows the user to crop an image.
 * Renamed to CustomCropImageContract to avoid project-wide redeclaration conflicts.
 */
class CustomCropImageContract : ActivityResultContract<CustomCropImageContractOptions, CropImageView.CropResult>() {
    override fun createIntent(context: Context, input: CustomCropImageContractOptions) =
        Intent(context, CropImageActivity::class.java).apply {
            // OPTIMIZATION: Ensure both read AND write permissions are granted.
            // The external activity needs write access to save the cropped output back to your app's cache.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            putExtra(
                CropImage.CROP_IMAGE_EXTRA_BUNDLE,
                Bundle(2).apply {
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, input.uri)
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, input.cropImageOptions)
                }
            )
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): CropImageView.CropResult {
        // OPTIMIZATION: Fast-fail to avoid executing reflection/parcelable extraction
        // if the user simply backed out of the camera/gallery without acting.
        if (intent == null || resultCode == Activity.RESULT_CANCELED) {
            return CropImage.CancelledResult
        }

        // OPTIMIZATION: Replaced custom extension with AndroidX IntentCompat.
        // This is Google's official, bulletproof method for cross-version Parcelable extraction.
        // It eliminates the need for file-wide deprecation suppression and protects against OEM crashes.
        val result = IntentCompat.getParcelableExtra(
            intent,
            CropImage.CROP_IMAGE_EXTRA_RESULT,
            CropImage.ActivityResult::class.java
        )

        return result ?: CropImage.CancelledResult
    }
}

/**
 * Options for the [CustomCropImageContract].
 */
data class CustomCropImageContractOptions(
    val uri: Uri?,
    val cropImageOptions: CropImageOptions,
)