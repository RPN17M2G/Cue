package io.github.rpn17m2g.cue.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegProcessRunnerTest {

    @Test
    fun `run passes the binary and arguments to the process and streams stderr lines`() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val process = FakeProcess(
            stderr = "frame=1 time=00:00:01.00\rframe=2 time=00:00:02.00\r",
            exitCode = 7,
        )
        val runner = FfmpegProcessRunner("libffmpeg.so", Dispatchers.IO) { command ->
            commands += command
            process
        }
        val lines = mutableListOf<String>()

        val exitCode = runner.run(listOf("-i", "in put.mp4", "-y", "out.mp3")) { lines += it }

        assertEquals(7, exitCode)
        assertEquals(listOf("libffmpeg.so", "-i", "in put.mp4", "-y", "out.mp3"), commands.single())
        assertEquals(listOf("frame=1 time=00:00:01.00", "frame=2 time=00:00:02.00"), lines)
        assertFalse(process.destroyed.get())
    }

    @Test
    fun `run destroys the process when the caller cancels`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val process = FakeProcess(stderr = "", exitCode = 0, blocking = true)
        val runner = FfmpegProcessRunner("libffmpeg.so", Dispatchers.IO) {
            started.complete(Unit)
            process
        }

        val job = launch(Dispatchers.Default) {
            runner.run(listOf("-i", "long.mp4")) { }
        }
        started.await()
        job.cancelAndJoin()

        assertTrue(process.destroyed.get())
        assertFalse(process.isAlive)
    }

    private class FakeProcess(
        stderr: String,
        private val exitCode: Int,
        blocking: Boolean = false,
    ) : Process() {

        private val alive = AtomicBoolean(true)
        val destroyed = AtomicBoolean(false)
        private val errorStream: InputStream = if (blocking) {
            BlockingInputStream { alive.set(false) }
        } else {
            ByteArrayInputStream(stderr.toByteArray())
        }

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = errorStream

        override fun waitFor(): Int {
            alive.set(false)
            return exitCode
        }

        override fun exitValue(): Int {
            check(!alive.get()) { "process is still alive" }
            return exitCode
        }

        override fun destroy() {
            destroyed.set(true)
            alive.set(false)
            runCatching { errorStream.close() }
        }

        override fun isAlive(): Boolean = alive.get()
    }

    private class BlockingInputStream(
        private val onClose: () -> Unit,
    ) : InputStream() {

        private val closed = AtomicBoolean(false)

        override fun read(): Int {
            while (!closed.get()) {
                Thread.sleep(1)
            }
            return -1
        }

        override fun close() {
            closed.set(true)
            onClose()
        }
    }
}
