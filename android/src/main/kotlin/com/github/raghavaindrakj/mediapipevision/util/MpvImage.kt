package com.github.raghavaindrakj.mediapipevision.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * [MediaPipeVision][com.github.raghavaindrakj.mediapipevision.MediaPipeVision] works with
 * [Bitmap]s, which carry no orientation metadata. Call [decodeUpright] when decoding from a
 * file/URI that might carry EXIF rotation (e.g. a camera photo), so the bitmap you pass to
 * `learn`/`recognize` is right-side up.
 */
object MpvImage {

    fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val bitmap = openStream(context, uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Could not decode image at $uri")

        val rotationDegrees = openStream(context, uri)?.use(::readRotationDegrees) ?: 0
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun openStream(context: Context, uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    private fun readRotationDegrees(input: InputStream): Int {
        val exif = ExifInterface(input)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}
