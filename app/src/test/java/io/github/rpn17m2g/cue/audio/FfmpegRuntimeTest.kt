package io.github.rpn17m2g.cue.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FfmpegRuntimeTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a missing binary reports a missing binary`() = runBlocking {
        val runtime = FfmpegRuntime(folder.root.absolutePath)

        val error = runCatching { runtime.verify() }.exceptionOrNull()

        assertTrue(error is FfmpegUnavailableException)
        assertEquals(FfmpegFailure.MISSING_BINARY, (error as FfmpegUnavailableException).reason)
    }

    @Test
    fun `a working binary verifies and passes the loader path`() = runBlocking {
        val binary = folder.newFile("libffmpeg.so")
        var environment: Map<String, String> = emptyMap()
        val runtime = FfmpegRuntime(
            nativeLibraryDir = folder.root.absolutePath,
            ioDispatcher = Dispatchers.IO,
            starter = { command, env ->
                assertEquals(listOf(binary.absolutePath, "-version"), command)
                environment = env
                CheckingProcess(exitCode = 0, finished = true)
            },
        )

        val path = runtime.verify()

        assertEquals(binary.absolutePath, path)
        assertEquals(
            "/system/lib64:${folder.root.absolutePath}",
            environment["LD_LIBRARY_PATH"],
        )
    }

    @Test
    fun `a timed out binary reports a timeout`() = runBlocking {
        folder.newFile("libffmpeg.so")
        val runtime = FfmpegRuntime(
            nativeLibraryDir = folder.root.absolutePath,
            ioDispatcher = Dispatchers.IO,
            starter = { _, _ -> CheckingProcess(exitCode = 0, finished = false) },
        )

        val error = runCatching { runtime.verify(timeoutMs = 1) }.exceptionOrNull()

        assertTrue(error is FfmpegUnavailableException)
        assertEquals(FfmpegFailure.TIMEOUT, (error as FfmpegUnavailableException).reason)
    }

    @Test
    fun `a non-zero exit reports the diagnostics`() = runBlocking {
        folder.newFile("libffmpeg.so")
        val runtime = FfmpegRuntime(
            nativeLibraryDir = folder.root.absolutePath,
            ioDispatcher = Dispatchers.IO,
            starter = { _, _ ->
                CheckingProcess(exitCode = 1, finished = true, stderr = "loader failed")
            },
        )

        val error = runCatching { runtime.verify() }.exceptionOrNull()

        assertTrue(error is FfmpegUnavailableException)
        assertEquals(FfmpegFailure.NON_ZERO_EXIT, (error as FfmpegUnavailableException).reason)
        assertTrue(error.message.orEmpty().contains("loader failed"))
    }

    @Test
    fun `a launch failure is typed`() = runBlocking {
        folder.newFile("libffmpeg.so")
        val runtime = FfmpegRuntime(
            nativeLibraryDir = folder.root.absolutePath,
            ioDispatcher = Dispatchers.IO,
            starter = { _, _ -> throw IOException("no exec") },
        )

        val error = runCatching { runtime.verify() }.exceptionOrNull()

        assertTrue(error is FfmpegUnavailableException)
        assertEquals(FfmpegFailure.LAUNCH_FAILED, (error as FfmpegUnavailableException).reason)
    }

    private class CheckingProcess(
        private val exitCode: Int,
        private val finished: Boolean,
        private val stderr: String = "ffmpeg version 6.0",
    ) : Process() {

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(stderr.toByteArray())

        override fun waitFor(): Int = exitCode

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = finished

        override fun exitValue(): Int = exitCode

        override fun destroy() = Unit
    }
}
