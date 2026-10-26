package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.audio.FfmpegCommandBuilder
import io.github.rpn17m2g.cue.audio.FfmpegProgressParser
import io.github.rpn17m2g.cue.audio.FfmpegRunner
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.domain.filename.FilenameRenderer
import io.github.rpn17m2g.cue.tags.TagField
import io.github.rpn17m2g.cue.tags.TagStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Convert, tag, and publish one job. The temp MP3 is removed on every exit path;
 * the input cache copy is owned by the caller so a retry can reuse it.
 */
class ProcessSongUseCase(
    private val runner: FfmpegRunner,
    private val tagStore: TagStore,
    private val outputStore: OutputStore,
    private val tempDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun execute(
        job: ProcessingJob,
        onProgress: (ProcessingStage, Float) -> Unit,
    ): SavedSong {
        onProgress(ProcessingStage.Preparing, 0f)
        val input = File(job.input.path)
        if (!input.isFile) {
            throw ProcessingException("The input file is no longer available")
        }
        val temp = withContext(ioDispatcher) {
            tempDirectory.mkdirs()
            File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, tempDirectory)
        }
        try {
            convert(job, temp, onProgress)
            coroutineContext.ensureActive()
            tag(job, temp, onProgress)
            coroutineContext.ensureActive()
            onProgress(ProcessingStage.Saving, SAVING_PROGRESS)
            val saved = outputStore.save(
                source = temp,
                displayNameBase = FilenameRenderer.render(job.draft, job.filenameTemplate),
                outputFolder = job.outputFolder,
            )
            onProgress(ProcessingStage.Saving, 1f)
            return saved
        } finally {
            temp.delete()
        }
    }

    private suspend fun convert(
        job: ProcessingJob,
        temp: File,
        onProgress: (ProcessingStage, Float) -> Unit,
    ) {
        onProgress(ProcessingStage.Converting, 0f)
        val args = FfmpegCommandBuilder.convertToMp3(
            inputPath = job.input.path,
            outputPath = temp.absolutePath,
            trim = job.trim,
            quality = job.quality,
        )
        val durationMs = job.trim?.durationMs ?: job.input.durationMs
        val parser = FfmpegProgressParser(durationMs)
        val exitCode = try {
            runner.run(args) { line ->
                parser.onLine(line)
                onProgress(ProcessingStage.Converting, parser.progress(durationMs) * CONVERT_PROGRESS_SCALE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw ProcessingException(
                "Could not run FFmpeg",
                listOfNotNull(e.message),
                e,
            )
        }
        if (exitCode != 0) {
            val diagnostics = parser.state.lastErrorLines.ifEmpty {
                listOf("FFmpeg exited with code $exitCode")
            }
            throw ProcessingException("FFmpeg exited with code $exitCode", diagnostics)
        }
        if (!temp.isFile || temp.length() == 0L) {
            throw ProcessingException("FFmpeg produced no audio", parser.state.lastErrorLines)
        }
    }

    private suspend fun tag(
        job: ProcessingJob,
        temp: File,
        onProgress: (ProcessingStage, Float) -> Unit,
    ) {
        onProgress(ProcessingStage.Tagging, TAGGING_PROGRESS)
        val fields = if (job.draft.artwork != null) {
            TagField.TEXT_FIELDS + TagField.ARTWORK
        } else {
            TagField.TEXT_FIELDS
        }
        try {
            withContext(ioDispatcher) {
                tagStore.writeInPlace(temp, job.draft, fields)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw ProcessingException(
                "Could not write tags",
                listOfNotNull(e.message),
                e,
            )
        }
    }

    private companion object {
        const val TEMP_PREFIX = "cue-process-"
        const val TEMP_SUFFIX = ".mp3"
        const val CONVERT_PROGRESS_SCALE = 0.9f
        const val TAGGING_PROGRESS = 0.93f
        const val SAVING_PROGRESS = 0.97f
    }
}
