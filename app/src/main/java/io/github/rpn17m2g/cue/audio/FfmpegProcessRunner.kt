package io.github.rpn17m2g.cue.audio

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Executes the FFmpeg binary and forwards its stderr lines. stdout is drained but
 * not exposed, so fingerprint output must target a file. Callers must decide
 * success from the returned exit code; stderr is only diagnostics.
 */
class FfmpegProcessRunner(
    private val binaryPath: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val startProcess: (List<String>) -> Process = ::startDefaultProcess,
) : FfmpegRunner {

    override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int = withContext(ioDispatcher) {
        val process = startProcess(listOf(binaryPath) + args)
        closeQuietly(process.outputStream)
        try {
            coroutineScope {
                val completed = AtomicBoolean(false)
                val destroyOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!completed.get()) terminate(process)
                    }
                }
                val stdoutDrain = launch {
                    process.inputStream.use { stream ->
                        val buffer = ByteArray(8 * 1_024)
                        while (stream.read(buffer) != -1) {
                            ensureActive()
                        }
                    }
                }
                val stderrReader = launch {
                    process.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            ensureActive()
                            onLine(line)
                        }
                    }
                }
                stderrReader.join()
                stdoutDrain.join()
                val exitCode = process.waitFor()
                completed.set(true)
                destroyOnCancel.cancel()
                exitCode
            }
        } finally {
            if (process.isAlive) terminate(process)
        }
    }

    private fun terminate(process: Process) {
        process.destroy()
        closeQuietly(process.outputStream)
        closeQuietly(process.inputStream)
        closeQuietly(process.errorStream)
        if (!waitForExit(process)) {
            process.destroyForcibly()
            waitForExit(process)
        }
    }

    private fun waitForExit(process: Process): Boolean = try {
        process.waitFor(2, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun closeQuietly(stream: Closeable) {
        try {
            stream.close()
        } catch (_: IOException) {
        }
    }
}

private fun startDefaultProcess(command: List<String>): Process = ProcessBuilder(command).start()
