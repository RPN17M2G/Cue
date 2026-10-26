package io.github.rpn17m2g.cue.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FfmpegProcessRunnerEnvironmentTest {

    @Test
    fun `the loader path is passed to the environment aware starter`() = runBlocking {
        var capturedEnvironment: Map<String, String> = emptyMap()
        var capturedCommand: List<String> = emptyList()
        val runner = FfmpegProcessRunner(
            binaryPath = "libffmpeg.so",
            ioDispatcher = Dispatchers.IO,
            environment = mapOf("LD_LIBRARY_PATH" to "/data/app/lib"),
            environmentStarter = { command, environment ->
                capturedCommand = command
                capturedEnvironment = environment
                ExitingProcess(exitCode = 0)
            },
        )

        val exitCode = runner.run(listOf("-version")) { }

        assertEquals(0, exitCode)
        assertEquals(mapOf("LD_LIBRARY_PATH" to "/data/app/lib"), capturedEnvironment)
        assertEquals(listOf("libffmpeg.so", "-version"), capturedCommand)
    }

    private class ExitingProcess(private val exitCode: Int) : Process() {
        private val stdout = ByteArrayInputStream(ByteArray(0))
        private val stderr = ByteArrayInputStream("ffmpeg version 6.0".toByteArray())

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = stdout

        override fun getErrorStream(): InputStream = stderr

        override fun waitFor(): Int = exitCode

        override fun exitValue(): Int = exitCode

        override fun destroy() = Unit
    }
}
