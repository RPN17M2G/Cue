package io.github.rpn17m2g.cue.tags

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Test-only [ArtworkEncoder] backed by ImageIO. AGP compiles unit tests without
 * `java.desktop`, so the JDK classes are reached reflectively. Production Android uses a
 * Bitmap-based implementation instead (EXIF rotation, white alpha matte, JPEG output).
 */
class ImageIoArtworkEncoder : ArtworkEncoder {

    override fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int): EncodedArtwork {
        require(maxDimension > 0) { "maxDimension must be positive" }
        val source = read(bytes)
        val width = width(source)
        val height = height(source)
        val scale = minOf(1.0, maxDimension.toDouble() / max(width, height))
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val target = newImage(targetWidth, targetHeight)
        draw(source, target, targetWidth, targetHeight)
        return EncodedArtwork(writeJpeg(target), JPEG_MIME_TYPE)
    }

    private companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 0.9f

        val imageIo: Class<*> = Class.forName("javax.imageio.ImageIO")
        val bufferedImage: Class<*> = Class.forName("java.awt.image.BufferedImage")
        val renderedImage: Class<*> = Class.forName("java.awt.image.RenderedImage")
        val renderingHints: Class<*> = Class.forName("java.awt.RenderingHints")
        val renderingHintKey: Class<*> = Class.forName("java.awt.RenderingHints\$Key")
        val graphics: Class<*> = Class.forName("java.awt.Graphics")
        val graphics2d: Class<*> = Class.forName("java.awt.Graphics2D")
        val image: Class<*> = Class.forName("java.awt.Image")
        val imageObserver: Class<*> = Class.forName("java.awt.image.ImageObserver")
        val colorClass: Class<*> = Class.forName("java.awt.Color")
        val imageWriteParam: Class<*> = Class.forName("javax.imageio.ImageWriteParam")
        val imageWriter: Class<*> = Class.forName("javax.imageio.ImageWriter")
        val imageMetadata: Class<*> = Class.forName("javax.imageio.metadata.IIOMetadata")
        val memoryOutput: Class<*> =
            Class.forName("javax.imageio.stream.MemoryCacheImageOutputStream")
        val imageInputStream: Class<*> = Class.forName("javax.imageio.stream.ImageInputStream")
        val iioImage: Class<*> = Class.forName("javax.imageio.IIOImage")
        val intType: Class<*> = Int::class.javaPrimitiveType!!
        val floatType: Class<*> = Float::class.javaPrimitiveType!!
        val intRgb: Int = bufferedImage.getField("TYPE_INT_RGB").getInt(null)
        val white: Any = checkNotNull(colorClass.getField("WHITE").get(null))
        val imageConstructor = bufferedImage.getConstructor(intType, intType, intType)
        val readMethod = imageIo.getMethod("read", java.io.InputStream::class.java)
        val getWidth = bufferedImage.getMethod("getWidth")
        val getHeight = bufferedImage.getMethod("getHeight")
        val createGraphics = bufferedImage.getMethod("createGraphics")
        val setColor = graphics.getMethod("setColor", colorClass)
        val fillRect = graphics.getMethod("fillRect", intType, intType, intType, intType)
        val dispose = graphics.getMethod("dispose")
        val setRenderingHint =
            graphics2d.getMethod("setRenderingHint", renderingHintKey, Any::class.java)
        val drawImage = graphics.getMethod(
            "drawImage",
            image,
            intType,
            intType,
            intType,
            intType,
            imageObserver,
        )

        fun read(bytes: ByteArray): Any {
            val result = readMethod.call(null, ByteArrayInputStream(bytes))
            return result ?: throw IllegalArgumentException("Unsupported image data")
        }

        fun width(value: Any): Int = getWidth.call(value) as Int

        fun height(value: Any): Int = getHeight.call(value) as Int

        fun newImage(width: Int, height: Int): Any =
            imageConstructor.newInstance(width, height, intRgb)

        fun draw(source: Any, target: Any, width: Int, height: Int) {
            val canvas = createGraphics.call(target)
            try {
                setColor.call(canvas, white)
                fillRect.call(canvas, 0, 0, width, height)
                setRenderingHint.call(
                    canvas,
                    renderingHints.getField("KEY_INTERPOLATION").get(null),
                    renderingHints.getField("VALUE_INTERPOLATION_BICUBIC").get(null),
                )
                setRenderingHint.call(
                    canvas,
                    renderingHints.getField("KEY_RENDERING").get(null),
                    renderingHints.getField("VALUE_RENDER_QUALITY").get(null),
                )
                drawImage.call(canvas, source, 0, 0, width, height, null)
            } finally {
                dispose.call(canvas)
            }
        }

        fun writeJpeg(target: Any): ByteArray {
            val writers = imageIo
                .getMethod("getImageWritersByFormatName", String::class.java)
                .call(null, "jpeg") as Iterator<*>
            if (!writers.hasNext()) {
                throw IllegalStateException("No JPEG writer available")
            }
            val writer = writers.next()!!
            val output = ByteArrayOutputStream()
            val stream = memoryOutput.getConstructor(OutputStream::class.java).newInstance(output)
            try {
                imageWriter.getMethod("setOutput", Any::class.java).call(writer, stream)
                val params = imageWriter.getMethod("getDefaultWriteParam").call(writer)
                val mode = imageWriteParam.getField("MODE_EXPLICIT").getInt(null)
                imageWriteParam.getMethod("setCompressionMode", intType).call(params, mode)
                imageWriteParam.getMethod("setCompressionQuality", floatType)
                    .call(params, JPEG_QUALITY)
                val frame = iioImage.constructors
                    .first {
                        it.parameterCount == 3 &&
                            it.parameterTypes[0] == renderedImage &&
                            it.parameterTypes[2] == imageMetadata
                    }
                    .newInstance(target, null, null)
                imageWriter.getMethod("write", imageMetadata, iioImage, imageWriteParam)
                    .call(writer, null, frame, params)
            } finally {
                imageInputStream.getMethod("close").call(stream)
                imageWriter.getMethod("dispose").call(writer)
            }
            return output.toByteArray()
        }

        fun Method.call(receiver: Any?, vararg args: Any?): Any? = try {
            invoke(receiver, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
