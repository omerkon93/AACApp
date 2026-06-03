@file:Suppress("DEPRECATION")
package com.kon.myaacapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView

/**
 * A custom [ActivityResultContract] to start an activity that allows the user to crop an image.
 * Renamed to CustomCropImageContract to avoid project-wide redeclaration conflicts.
 */
class CustomCropImageContract : ActivityResultContract<CustomCropImageContractOptions, CropImageView.CropResult>() {
    override fun createIntent(context: Context, input: CustomCropImageContractOptions) =
        Intent(context, CropImageActivity::class.java).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(
                CropImage.CROP_IMAGE_EXTRA_BUNDLE,
                Bundle(2).apply {
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, input.uri)
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, input.cropImageOptions)
                },
            )
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): CropImageView.CropResult {
        // Now explicitly calls the private extension function defined below
        val result = intent?.getSafeParcelableExtraCompat<CropImage.ActivityResult>(CropImage.CROP_IMAGE_EXTRA_RESULT)

        return if (result == null || resultCode == Activity.RESULT_CANCELED) {
            CropImage.CancelledResult
        } else {
            result
        }
    }
}

/**
 * Options for the [CustomCropImageContract].
 */
data class CustomCropImageContractOptions(
    val uri: Uri?,
    val cropImageOptions: CropImageOptions,
)

/**
 * Helper to get parcelable extra in a backward compatible way.
 * Marked as 'private' and renamed to avoid conflicting overloads with other utility files.
 */
private inline fun <reified T : Parcelable> Intent.getSafeParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}