package io.github.rpn17m2g.cue.tags

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

class BitmapArtworkEncoder : ArtworkEncoder {

    override fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int): EncodedArtwork {
        require(maxDimension > 0) { "maxDimension must be positive" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image data" }

        val sample = ArtworkScaling.sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("Unsupported image data")

        val target = ArtworkScaling.fitInside(decoded.width, decoded.height, maxDimension)
        val scaled = if (target.width == decoded.width && target.height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
        }
        val flattened = flattenOnWhite(scaled)
        return try {
            val output = ByteArrayOutputStream()
            check(flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Could not encode artwork"
            }
            EncodedArtwork(output.toByteArray(), JPEG_MIME_TYPE)
        } finally {
            flattened.recycle()
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return flattened
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val JPEG_MIME_TYPE = "image/jpeg"
    }
}
