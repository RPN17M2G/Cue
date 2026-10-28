package io.github.rpn17m2g.cue.audio

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class FfmpegFailure {
    MISSING_BINARY,
    LAUNCH_FAILED,
    TIMEOUT,
    NON_ZERO_EXIT,
}

class FfmpegUnavailableException(
    val reason: FfmpegFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Locates the FFmpeg executable packaged as `libffmpeg.so` and proves it can run
 * with the loader path set to the app's native library directory.
 *
 * The app also ships Tesseract's stack, which bundles its own `libjpeg.so` and
 * `libpngx.so`. The FFmpeg binary runs as a standalone process, so its loader
 * path applies to every dependency it pulls in — including platform libraries
 * such as `libhwui`, which then resolves `libjpeg.so` from the app directory and
 * dies on a symbol the bundled copy does not export. Platform directories are
 * therefore searched first: system libraries resolve to the platform's copies,
 * while the app directory still supplies the FFmpeg stack, which has no system
 * counterpart.
 */
class FfmpegRuntime(
    private val nativeLibraryDir: String,
    private val binaryName: String = BINARY_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val starter: FfmpegProcessStarter = FfmpegProcessStarter.System,
    private val systemLibraryDir: String = DEFAULT_SYSTEM_LIBRARY_DIR,
) {

    val binaryPath: String = File(nativeLibraryDir, binaryName).absolutePath

    val environment: Map<String, String> = mapOf(
        LD_LIBRARY_PATH to "$systemLibraryDir:$nativeLibraryDir",
    )

    suspend fun verify(timeoutMs: Long = VERIFY_TIMEOUT_MS): String = withContext(ioDispatcher) {
        val binary = File(binaryPath)
        if (!binary.isFile) {
            throw FfmpegUnavailableException(
                FfmpegFailure.MISSING_BINARY,
                "FFmpeg binary is missing at $binaryPath",
            )
        }
        val process = try {
            starter.start(listOf(binaryPath, "-version"), environment)
        } catch (e: IOException) {
            throw FfmpegUnavailableException(
                FfmpegFailure.LAUNCH_FAILED,
                "Could not start FFmpeg",
                e,
            )
        }
        try {
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw FfmpegUnavailableException(
                    FfmpegFailure.TIMEOUT,
                    "FFmpeg did not answer within ${timeoutMs}ms",
                )
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (process.exitValue() != 0) {
                throw FfmpegUnavailableException(
                    FfmpegFailure.NON_ZERO_EXIT,
                    "FFmpeg exited with ${process.exitValue()}: ${(stderr + stdout).trim()}",
                )
            }
            binaryPath
        } catch (e: IOException) {
            throw FfmpegUnavailableException(
                FfmpegFailure.LAUNCH_FAILED,
                "Could not read FFmpeg output",
                e,
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private companion object {
        const val BINARY_NAME = "libffmpeg.so"
        const val LD_LIBRARY_PATH = "LD_LIBRARY_PATH"
        const val VERIFY_TIMEOUT_MS = 10_000L

        // Cue ships 64-bit FFmpeg only, so the app never runs as a 32-bit
        // process and /system/lib64 is always the platform library directory.
        const val DEFAULT_SYSTEM_LIBRARY_DIR = "/system/lib64"
    }
}
