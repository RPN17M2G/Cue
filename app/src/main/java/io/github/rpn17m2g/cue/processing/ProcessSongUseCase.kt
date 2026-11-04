package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.audio.FfmpegCommandBuilder
import io.github.rpn17m2g.cue.audio.FfmpegProgressParser
import io.github.rpn17m2g.cue.audio.FfmpegRunner
import io.github.rpn17m2g.cue.data.input.DocumentEditor
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.data.output.SourceOutcome
import io.github.rpn17m2g.cue.domain.filename.FilenameRenderer
import io.github.rpn17m2g.cue.domain.model.TrimMath
import io.github.rpn17m2g.cue.domain.model.matchesWritten
import io.github.rpn17m2g.cue.tags.TagField
import io.github.rpn17m2g.cue.tags.TagStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Converts and publishes a new file, or rewrites the tags of an existing MP3 in
 * place. Temp files are removed on every exit path; the input cache copy is owned
 * by the caller so a retry can reuse it.
 */
class ProcessSongUseCase(
    private val runner: FfmpegRunner,
    private val tagStore: TagStore,
    private val outputStore: OutputStore,
    private val documentEditor: DocumentEditor,
    private val tempDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun execute(
        job: ProcessingJob,
        onProgress: (ProcessingStage, Float) -> Unit,
    ): SavedSong = when (job.mode) {
        ProcessingMode.CONVERT_AND_SAVE -> convertAndSave(job, onProgress)
        ProcessingMode.EDIT_IN_PLACE -> editInPlace(job, onProgress)
    }

    private suspend fun convertAndSave(
        job: ProcessingJob,
        onProgress: (ProcessingStage, Float) -> Unit,
    ): SavedSong {
        onProgress(ProcessingStage.Preparing, 0f)
        val input = File(job.input.path)
        if (!input.isFile) {
            throw ProcessingException("The input file is no longer available")
        }
        val temp = createTemp()
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
            // The tags were written to the temp file, but only the published
            // row proves the copy survived MediaStore. Nothing may delete the
            // source before this read-back succeeds.
            verifySaved(saved, job, temp.length())
            onProgress(ProcessingStage.Saving, 1f)
            val outcome = if (job.deleteSource && job.input.kind == InputKind.VIDEO) {
                SourceOutcome.PENDING
            } else {
                SourceOutcome.NONE
            }
            return saved.copy(
                title = job.draft.title,
                artist = job.draft.artist,
                durationMs = TrimMath.effectiveDurationMs(job.trim, job.input.durationMs),
                artwork = job.draft.artwork,
                sourceUri = job.input.uri.takeIf { outcome == SourceOutcome.PENDING },
                sourceOutcome = outcome,
            )
        } finally {
            temp.delete()
        }
    }

    /**
     * Reads the published file back through [OutputStore] and compares its size
     * and tags with what was written. A partial MediaStore copy or a provider
     * that dropped a frame must fail here, before the Saved screen offers to
     * delete the source.
     */
    private suspend fun verifySaved(saved: SavedSong, job: ProcessingJob, expectedBytes: Long) {
        val copy = createTemp()
        try {
            outputStore.copyOut(saved.uri, copy)
            if (copy.length() != expectedBytes) {
                throw ProcessingException(
                    "The saved file is ${copy.length()} bytes instead of $expectedBytes",
                )
            }
            val written = withContext(ioDispatcher) {
                runCatching { tagStore.read(copy) }.getOrNull()
            } ?: throw ProcessingException("The saved file has no readable tag")
            val fields = if (job.draft.artwork != null) {
                TagField.TEXT_FIELDS + TagField.ARTWORK
            } else {
                TagField.TEXT_FIELDS
            }
            if (!written.matchesWritten(job.draft, fields)) {
                throw ProcessingException("The saved tags read back differently from what was written")
            }
        } catch (cancelled: CancellationException) {
            // The row was already published; remove it even though the job is
            // being cancelled, otherwise a half-verified file stays visible.
            withContext(NonCancellable) {
                runCatching { outputStore.delete(saved.uri) }
            }
            throw cancelled
        } catch (e: Exception) {
            // Do not leave a file whose bytes or tags could not be confirmed.
            withContext(NonCancellable) {
                runCatching { outputStore.delete(saved.uri) }
            }
            throw e
        } finally {
            copy.delete()
        }
    }

    /**
     * Edits a copy of the cache file and publishes it back to the source
     * document. The untouched cache copy is the restore point when the provider
     * fails mid-write or the read-back does not match.
     */
    private suspend fun editInPlace(
        job: ProcessingJob,
        onProgress: (ProcessingStage, Float) -> Unit,
    ): SavedSong {
        onProgress(ProcessingStage.Preparing, 0f)
        val original = File(job.input.path)
        if (!original.isFile) {
            throw ProcessingException("The input file is no longer available")
        }
        if (job.input.kind != InputKind.MP3) {
            throw ProcessingException("Only MP3 files can be edited in place")
        }
        val uri = job.input.uri
        if (uri.isBlank()) {
            throw ProcessingException("The source file cannot be updated")
        }

        val temp = createTemp()
        try {
            withContext(ioDispatcher) { original.copyTo(temp, overwrite = true) }
            if (job.changedFields.isNotEmpty()) {
                writeTags(job, temp, onProgress)
                coroutineContext.ensureActive()
                onProgress(ProcessingStage.Saving, SAVING_PROGRESS)
                publishInPlace(uri, temp, original, job)
            }
            onProgress(ProcessingStage.Saving, 1f)
            return SavedSong(
                uri = job.input.uri,
                displayName = job.input.displayName,
                relativePath = "",
                inPlaceEdit = true,
                title = job.draft.title,
                artist = job.draft.artist,
                durationMs = job.input.durationMs,
                artwork = job.draft.artwork,
            )
        } finally {
            temp.delete()
        }
    }

    private suspend fun publishInPlace(
        uri: String,
        edited: File,
        original: File,
        job: ProcessingJob,
    ) {
        try {
            documentEditor.replace(uri, edited)
            verify(uri, job)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val restored = runCatching { documentEditor.replace(uri, original) }.isSuccess
            throw ProcessingException(
                if (restored) {
                    "Could not update the original file"
                } else {
                    "Could not update the original file and the restore failed"
                },
                listOfNotNull(e.message, if (restored) null else "The source may be incomplete"),
                e,
            )
        }
    }

    private suspend fun verify(uri: String, job: ProcessingJob) {
        val verification = createTemp()
        try {
            documentEditor.copyTo(uri, verification)
            val written = withContext(ioDispatcher) {
                runCatching { tagStore.read(verification) }.getOrNull()
            } ?: throw ProcessingException("The updated file has no readable tag")
            if (!written.matchesWritten(job.draft, job.changedFields)) {
                throw ProcessingException("The tags read back differently from what was written")
            }
        } finally {
            verification.delete()
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
        val fields = if (job.draft.artwork != null) {
            TagField.TEXT_FIELDS + TagField.ARTWORK
        } else {
            TagField.TEXT_FIELDS
        }
        writeTags(job, temp, onProgress, fields)
    }

    private suspend fun writeTags(
        job: ProcessingJob,
        target: File,
        onProgress: (ProcessingStage, Float) -> Unit,
        fields: Set<TagField> = job.changedFields,
    ) {
        onProgress(ProcessingStage.Tagging, TAGGING_PROGRESS)
        try {
            withContext(ioDispatcher) {
                tagStore.writeInPlace(target, job.draft, fields)
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

    private suspend fun createTemp(): File = withContext(ioDispatcher) {
        tempDirectory.mkdirs()
        File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, tempDirectory)
    }

    private companion object {
        const val TEMP_PREFIX = "cue-process-"
        const val TEMP_SUFFIX = ".mp3"
        const val CONVERT_PROGRESS_SCALE = 0.9f
        const val TAGGING_PROGRESS = 0.93f
        const val SAVING_PROGRESS = 0.97f
    }
}
