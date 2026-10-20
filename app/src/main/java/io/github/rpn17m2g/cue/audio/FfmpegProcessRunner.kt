package io.github.rpn17m2g.cue.audio

import java.io.IOException
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
import kotlin.coroutines.coroutineContext

/**
 * Executes the FFmpeg binary and forwards its stderr lines. stdout is discarded;
 * fingerprint output should be written to a file when run through this runner.
 */
class FfmpegProcessRunner(
    private val binaryPath: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val startProcess: (List<String>) -> Process = ::startDefaultProcess,
) : FfmpegRunner {

    override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int = withContext(ioDispatcher) {
        val process = startProcess(listOf(binaryPath) + args)
        closeQuietly(process)
        try {
            coroutineScope {
                val completed = AtomicBoolean(false)
                val destroyOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!completed.get()) process.destroy()
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
                    process.errorStream.bufferedReader().useLines { lines ->
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
            if (process.isAlive) process.destroy()
        }
    }

    private fun closeQuietly(process: Process) {
        try {
            process.outputStream.close()
        } catch (_: IOException) {
        }
    }
}

private fun startDefaultProcess(command: List<String>): Process = ProcessBuilder(command).start()
