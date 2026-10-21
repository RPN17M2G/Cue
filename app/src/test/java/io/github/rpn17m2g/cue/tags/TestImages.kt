package io.github.rpn17m2g.cue.tags

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException

/**
 * Test image factory. AGP compiles unit tests without `java.desktop`, so ImageIO and
 * BufferedImage are reached reflectively; at test runtime they are ordinary JDK classes.
 */
object TestImages {

    fun png(width: Int, height: Int): ByteArray = encode(width, height, "png")

    fun jpeg(width: Int, height: Int): ByteArray = encode(width, height, "jpeg")

    fun size(bytes: ByteArray): Pair<Int, Int> {
        val image = invoke(read, null, ByteArrayInputStream(bytes))
            ?: error("Could not decode test image")
        return width(image) to height(image)
    }

    private val imageIo = Class.forName("javax.imageio.ImageIO")
    private val bufferedImage = Class.forName("java.awt.image.BufferedImage")
    private val renderedImage = Class.forName("java.awt.image.RenderedImage")
    private val graphics = Class.forName("java.awt.Graphics")
    private val colorClass = Class.forName("java.awt.Color")
    private val intType = Int::class.javaPrimitiveType!!
    private val read = imageIo.getMethod("read", java.io.InputStream::class.java)
    private val write = imageIo.getMethod("write", renderedImage, String::class.java, OutputStream::class.java)
    private val getWidth = bufferedImage.getMethod("getWidth")
    private val getHeight = bufferedImage.getMethod("getHeight")
    private val createGraphics = bufferedImage.getMethod("createGraphics")
    private val newImage = bufferedImage.getConstructor(intType, intType, intType)
    private val newColor = colorClass.getConstructor(intType, intType, intType)
    private val setColor = graphics.getMethod("setColor", colorClass)
    private val fillRect = graphics.getMethod("fillRect", intType, intType, intType, intType)
    private val dispose = graphics.getMethod("dispose")
    private val intRgb = bufferedImage.getField("TYPE_INT_RGB").getInt(null)

    private fun encode(width: Int, height: Int, format: String): ByteArray {
        val image = newImage.newInstance(width, height, intRgb)
        val canvas = createGraphics.invoke(image)
        try {
            setColor.invoke(canvas, newColor.newInstance(32, 96, 160))
            fillRect.invoke(canvas, 0, 0, width, height)
        } finally {
            dispose.invoke(canvas)
        }
        val output = ByteArrayOutputStream()
        val written = invoke(write, null, image, format, output) as Boolean
        check(written) { "No ImageIO writer for $format" }
        return output.toByteArray()
    }

    private fun width(image: Any): Int = getWidth.invoke(image) as Int

    private fun height(image: Any): Int = getHeight.invoke(image) as Int

    private fun invoke(
        method: java.lang.reflect.Method,
        receiver: Any?,
        vararg args: Any?,
    ): Any? = try {
        method.invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
