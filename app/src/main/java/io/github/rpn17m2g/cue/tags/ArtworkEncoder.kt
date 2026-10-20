package io.github.rpn17m2g.cue.tags

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * JVM cover-art resizing. The Android layer substitutes a Bitmap-backed implementation.
 *
 * ImageIO lives in `java.desktop`, which is not on Android's compile classpath, so the
 * bridge reaches it reflectively. Calling this on Android fails loudly instead of
 * breaking compilation for the whole app.
 */
object ArtworkEncoder {

    fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int = 1000): ByteArray {
        require(maxDimension > 0) { "maxDimension must be positive" }
        val source = ImageIo.read(bytes)
        val width = ImageIo.width(source)
        val height = ImageIo.height(source)
        if (width <= maxDimension && height <= maxDimension) {
            return bytes
        }

        val scale = maxDimension.toDouble() / max(width, height)
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val target = ImageIo.newImage(targetWidth, targetHeight)
        ImageIo.draw(source, target, targetWidth, targetHeight)
        return ImageIo.writeJpeg(target, JPEG_QUALITY)
    }

    private const val JPEG_QUALITY = 0.9f
}

private object ImageIo {

    private val imageIo = load("javax.imageio.ImageIO")
    private val bufferedImage = load("java.awt.image.BufferedImage")
    private val renderingHints = load("java.awt.RenderingHints")
    private val renderingHintKey = load("java.awt.RenderingHints\$Key")
    private val graphics = load("java.awt.Graphics2D")
    private val image = load("java.awt.Image")
    private val imageObserver = load("java.awt.image.ImageObserver")
    private val imageWriteParam = load("javax.imageio.ImageWriteParam")
    private val imageWriter = load("javax.imageio.ImageWriter")
    private val imageMetadata = load("javax.imageio.metadata.IIOMetadata")
    private val memoryOutput = load("javax.imageio.stream.MemoryCacheImageOutputStream")
    private val imageInputStream = load("javax.imageio.stream.ImageInputStream")
    private val iioImage = load("javax.imageio.IIOImage")
    private val intType = Int::class.javaPrimitiveType!!
    private val floatType = Float::class.javaPrimitiveType!!
    private val intRgb = bufferedImage.getField("TYPE_INT_RGB").getInt(null)
    private val imageConstructor = bufferedImage.getConstructor(intType, intType, intType)
    private val read = imageIo.getMethod("read", java.io.InputStream::class.java)
    private val getWidth = bufferedImage.getMethod("getWidth")
    private val getHeight = bufferedImage.getMethod("getHeight")
    private val createGraphics = bufferedImage.getMethod("createGraphics")
    private val setRenderingHint = graphics.getMethod("setRenderingHint", renderingHintKey, Any::class.java)
    private val drawImage = graphics.getMethod(
        "drawImage",
        image,
        intType,
        intType,
        intType,
        intType,
        imageObserver,
    )
    private val disposeGraphics = graphics.getMethod("dispose")

    fun read(bytes: ByteArray): Any {
        val result = try {
            read.invoke(null, ByteArrayInputStream(bytes))
        } catch (e: InvocationTargetException) {
            throw IllegalArgumentException("Unsupported image data", e.targetException)
        }
        return result ?: throw IllegalArgumentException("Unsupported image data")
    }

    fun width(image: Any): Int = getWidth.invoke(image) as Int

    fun height(image: Any): Int = getHeight.invoke(image) as Int

    fun newImage(width: Int, height: Int): Any =
        imageConstructor.newInstance(width, height, intRgb)

    fun draw(source: Any, target: Any, width: Int, height: Int) {
        val canvas = createGraphics.invoke(target)
        try {
            setRenderingHint.invoke(
                canvas,
                renderingHints.getField("KEY_INTERPOLATION").get(null),
                renderingHints.getField("VALUE_INTERPOLATION_BICUBIC").get(null),
            )
            setRenderingHint.invoke(
                canvas,
                renderingHints.getField("KEY_RENDERING").get(null),
                renderingHints.getField("VALUE_RENDER_QUALITY").get(null),
            )
            drawImage.invoke(canvas, source, 0, 0, width, height, null)
        } finally {
            disposeGraphics.invoke(canvas)
        }
    }

    fun writeJpeg(target: Any, quality: Float): ByteArray {
        val writers = imageIo.getMethod("getImageWritersByFormatName", String::class.java)
            .invoke(null, "jpeg") as Iterator<*>
        if (!writers.hasNext()) {
            throw IllegalStateException("No JPEG writer available")
        }
        val writer = writers.next()!!
        val output = ByteArrayOutputStream()
        val stream = memoryOutput.getConstructor(OutputStream::class.java).newInstance(output)
        val closeStream = imageInputStream.getMethod("close")
        try {
            imageWriter.getMethod("setOutput", Any::class.java).invoke(writer, stream)
            val params = imageWriter.getMethod("getDefaultWriteParam").invoke(writer)
            val mode = imageWriteParam.getField("MODE_EXPLICIT").getInt(null)
            imageWriteParam.getMethod("setCompressionMode", intType).invoke(params, mode)
            imageWriteParam.getMethod("setCompressionQuality", floatType)
                .invoke(params, quality)
            val frame = iioImage.constructors
                .first { it.parameterCount == 3 }
                .newInstance(target, null, null)
            imageWriter.getMethod("write", imageMetadata, iioImage, imageWriteParam)
                .invoke(writer, null, frame, params)
        } finally {
            closeStream.invoke(stream)
            imageWriter.getMethod("dispose").invoke(writer)
        }
        return output.toByteArray()
    }

    private fun load(className: String): Class<*> = try {
        Class.forName(className)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException("javax.imageio is not available on this platform", e)
    }
}

