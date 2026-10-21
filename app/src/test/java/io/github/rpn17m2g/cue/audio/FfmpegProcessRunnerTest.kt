package io.github.rpn17m2g.cue.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    @Test(timeout = 10_000)
    fun `run drains large stdout so the process cannot deadlock on a full pipe`() = runBlocking {
        val process = FakeProcess(stderr = "", exitCode = 0, stdoutBytes = 1_048_576)
        val runner = FfmpegProcessRunner("libffmpeg.so", Dispatchers.IO) { process }

        val exitCode = runner.run(listOf("-i", "long.mp4", "fingerprint.tmp")) { }

        assertEquals(0, exitCode)
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

    @Test
    fun `run propagates cancellation to the caller`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val process = FakeProcess(stderr = "", exitCode = 0, blocking = true)
        val runner = FfmpegProcessRunner("libffmpeg.so", Dispatchers.IO) {
            started.complete(Unit)
            process
        }

        val deferred = async(Dispatchers.Default) {
            runner.run(listOf("-i", "long.mp4")) { }
        }
        started.await()
        deferred.cancel()

        val cause = runCatching { deferred.await() }.exceptionOrNull()

        assertTrue("expected CancellationException but got $cause", cause is CancellationException)
        assertTrue(process.destroyed.get())
    }

    private class FakeProcess(
        stderr: String,
        private val exitCode: Int,
        blocking: Boolean = false,
        stdoutBytes: Int = 0,
    ) : Process() {

        private val alive = AtomicBoolean(true)
        val destroyed = AtomicBoolean(false)
        private val writerDone = CountDownLatch(if (stdoutBytes > 0) 1 else 0)
        private val errorStream: InputStream = if (blocking) {
            BlockingInputStream()
        } else {
            ByteArrayInputStream(stderr.toByteArray())
        }
        private val stdoutStream: InputStream = if (stdoutBytes > 0) {
            startStdoutWriter(stdoutBytes)
        } else {
            ByteArrayInputStream(ByteArray(0))
        }

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = stdoutStream

        override fun getErrorStream(): InputStream = errorStream

        override fun waitFor(): Int {
            writerDone.await()
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
        }

        override fun isAlive(): Boolean = alive.get()

        private fun startStdoutWriter(bytes: Int): InputStream {
            val pipe = PipedInputStream(64 * 1_024)
            val writer = Thread({
                PipedOutputStream(pipe).use { output ->
                    val chunk = ByteArray(8 * 1_024) { 'x'.code.toByte() }
                    var remaining = bytes
                    while (remaining > 0) {
                        val count = minOf(remaining, chunk.size)
                        output.write(chunk, 0, count)
                        remaining -= count
                    }
                }
                writerDone.countDown()
            }, "fake-ffmpeg-stdout")
            writer.isDaemon = true
            writer.start()
            return pipe
        }
    }

    private class BlockingInputStream : InputStream() {

        private val closed = AtomicBoolean(false)

        override fun read(): Int {
            while (!closed.get()) {
                Thread.sleep(1)
            }
            return -1
        }

        override fun close() {
            closed.set(true)
        }
    }
}
