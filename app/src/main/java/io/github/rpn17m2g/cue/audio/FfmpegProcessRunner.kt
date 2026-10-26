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
    private val environment: Map<String, String> = emptyMap(),
    private val environmentStarter: FfmpegProcessStarter? = null,
    private val startProcess: (List<String>) -> Process = ::startDefaultProcess,
) : FfmpegRunner {

    override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int = withContext(ioDispatcher) {
        val command = listOf(binaryPath) + args
        val process = when {
            environmentStarter != null -> environmentStarter.start(command, environment)
            environment.isEmpty() -> startProcess(command)
            else -> FfmpegProcessStarter.System.start(command, environment)
        }
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

/**
 * Starts FFmpeg with an explicit environment. Android cannot exec a library from
 * the APK directly, so the binary lives in the native library directory and the
 * loader needs `LD_LIBRARY_PATH` pointing there to find its shared dependencies.
 */
fun interface FfmpegProcessStarter {
    fun start(command: List<String>, environment: Map<String, String>): Process

    companion object {
        val System: FfmpegProcessStarter = FfmpegProcessStarter(::startProcessWithEnvironment)
    }
}

private fun startDefaultProcess(command: List<String>): Process = ProcessBuilder(command).start()

private fun startProcessWithEnvironment(
    command: List<String>,
    environment: Map<String, String>,
): Process {
    val builder = ProcessBuilder(command)
    if (environment.isNotEmpty()) {
        builder.environment().putAll(environment)
    }
    return builder.start()
}
