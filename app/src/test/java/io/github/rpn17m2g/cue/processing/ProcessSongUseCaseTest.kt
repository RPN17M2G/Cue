package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.audio.FfmpegRunner
import io.github.rpn17m2g.cue.data.input.DocumentEditor
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange
import io.github.rpn17m2g.cue.tags.TagField
import io.github.rpn17m2g.cue.tags.TagStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProcessSongUseCaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tagStore = RecordingTagStore()
    private val outputStore = RecordingOutputStore()

    @Test
    fun `a successful job runs every stage and deletes its temp file`() = runTest {
        val input = inputFile()
        val tempDir = File(folder.root, "work")
        val stages = mutableListOf<ProcessingStage>()
        val useCase = useCase(WritingRunner(exitCode = 0, lines = listOf("out_time_ms=5000000")), tempDir)

        val saved = useCase.execute(job(input), onProgress = { stage, progress ->
            assertTrue(progress in 0f..1f)
            stages += stage
        })

        assertEquals("Daft Punk - Get Lucky.mp3", saved.displayName)
        assertEquals(listOf(ProcessingStage.Preparing, ProcessingStage.Converting, ProcessingStage.Tagging, ProcessingStage.Saving), stages.distinct())
        assertEquals(TagField.TEXT_FIELDS, tagStore.fields)
        assertEquals("Daft Punk", tagStore.draft?.artist)
        assertTrue(outputStore.saved)
        assertEquals(0, tempDir.listFiles()?.size)
        assertTrue(input.exists())
    }

    @Test
    fun `the progress callback scales conversion time against the track duration`() = runTest {
        val input = inputFile()
        val progress = mutableListOf<Float>()
        val useCase = useCase(
            WritingRunner(exitCode = 0, lines = listOf("out_time_ms=5000000")),
            File(folder.root, "work"),
        )

        useCase.execute(job(input)) { stage, value ->
            if (stage == ProcessingStage.Converting) progress += value
        }

        assertEquals(0.45f, progress.max(), 0.001f)
    }

    @Test
    fun `a trim window drives the conversion arguments`() = runTest {
        val input = inputFile()
        val runner = WritingRunner(exitCode = 0, lines = emptyList())
        val useCase = useCase(runner, File(folder.root, "work"))

        useCase.execute(job(input, trim = TrimRange(2_000, 8_000))) { _, _ -> }

        assertTrue(runner.args.containsAll(listOf("-ss", "2.000", "-t", "6.000")))
    }

    @Test
    fun `artwork is added to the tag fields when present`() = runTest {
        val input = inputFile()
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), File(folder.root, "work"))
        val artwork = Artwork(byteArrayOf(1, 2, 3))
        val artworkJob = job(input).copy(draft = job(input).draft.copy(artwork = artwork))

        useCase.execute(artworkJob) { _, _ -> }

        assertEquals(TagField.TEXT_FIELDS + TagField.ARTWORK, tagStore.fields)
    }

    @Test
    fun `a non-zero exit fails with the stderr diagnostics and cleans the temp file`() = runTest {
        val input = inputFile()
        val tempDir = File(folder.root, "work")
        val useCase = useCase(
            WritingRunner(exitCode = 2, lines = listOf("Error opening input", "Conversion failed!")),
            tempDir,
        )

        val error = runCatching {
            useCase.execute(job(input)) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertTrue((error as ProcessingException).details.contains("Error opening input"))
        assertFalse(outputStore.saved)
        assertEquals(0, tempDir.listFiles()?.size)
    }

    @Test
    fun `a conversion that produces no audio is a typed failure`() = runTest {
        val input = inputFile()
        val tempDir = File(folder.root, "work")
        val useCase = useCase(SilentRunner(lines = listOf("Could not open output file")), tempDir)

        val error = runCatching {
            useCase.execute(job(input)) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is NoAudioException)
        // The diagnostics still travel with it, so the details panel is not empty.
        assertEquals(listOf("Could not open output file"), (error as NoAudioException).details)
        assertFalse(outputStore.saved)
        assertEquals(0, tempDir.listFiles()?.size)
    }

    @Test
    fun `a trim in place converts, tags every field and replaces the document`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val runner = WritingRunner(exitCode = 0, lines = emptyList())
        val editor = FakeDocumentEditor()
        val tempDir = File(folder.root, "work")
        val useCase = useCase(runner, tempDir, editor)
        val trimJob = inPlaceJob(input, emptySet()).copy(
            mode = ProcessingMode.TRIM_IN_PLACE,
            trim = TrimRange(2_000, 8_000),
        )

        val saved = useCase.execute(trimJob) { _, _ -> }

        assertTrue(runner.args.containsAll(listOf("-ss", "2.000", "-t", "6.000")))
        assertEquals(TagField.TEXT_FIELDS, tagStore.fields)
        assertEquals(1, editor.writes.size)
        assertTrue(saved.inPlaceEdit)
        assertTrue(saved.trimmedInPlace)
        assertEquals(6_000L, saved.durationMs)
        assertEquals("content://doc/song.mp3", saved.uri)
        // The gallery row has to learn the new length after the audio changed.
        assertEquals(
            listOf("content://doc/song.mp3" to 6_000L),
            outputStore.refreshed,
        )
        assertEquals(0, tempDir.listFiles()?.size)
    }

    @Test
    fun `a failed trim in place restores the original document`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val editor = FakeDocumentEditor().apply { failNextWrite = true }
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), folder.root, editor)
        val trimJob = inPlaceJob(input, emptySet()).copy(
            mode = ProcessingMode.TRIM_IN_PLACE,
            trim = TrimRange(2_000, 8_000),
        )

        val error = runCatching {
            useCase.execute(trimJob) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        // The fake records only writes the provider accepted, so the single
        // entry here is the restore of the untouched document.
        assertEquals(1, editor.writes.size)
        assertArrayEquals(input.readBytes(), editor.writes.single())
    }

    @Test
    fun `cancelling during the read-back restores the original document`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val editor = FakeDocumentEditor().apply { cancelNextRead = true }
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), folder.root, editor)

        val error = runCatching {
            useCase.execute(inPlaceJob(input, setOf(TagField.TITLE))) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        // The edited bytes were already accepted; the original has to go back
        // before the cancellation unwinds, or the document stays half-updated.
        assertEquals(2, editor.writes.size)
    }

    @Test
    fun `a missing input fails before conversion`() = runTest {
        val missing = ResolvedInput(
            id = "missing",
            uri = "content://missing",
            path = File(folder.root, "gone.mp4").absolutePath,
            displayName = "gone.mp4",
            mimeType = "video/mp4",
            kind = InputKind.VIDEO,
            durationMs = 10_000,
        )
        val runner = WritingRunner(exitCode = 0, lines = emptyList())
        val useCase = useCase(runner, File(folder.root, "work"))

        val error = runCatching {
            useCase.execute(job(missing)) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertTrue(runner.args.isEmpty())
    }

    @Test
    fun `cancellation propagates and removes the temp file`() = runTest {
        val input = inputFile()
        val tempDir = File(folder.root, "work")
        val started = CompletableDeferred<Unit>()
        val runner = FfmpegRunner { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }
        val useCase = useCase(runner, tempDir)

        val deferred = async(Dispatchers.Default) {
            useCase.execute(job(input)) { _, _ -> }
        }
        started.await()
        deferred.cancelAndJoin()

        assertTrue(deferred.isCancelled)
        withContext(Dispatchers.IO) {
            assertEquals(0, tempDir.listFiles()?.size)
        }
    }

    @Test
    fun `an in-place job writes only changed tags and skips ffmpeg`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val runner = WritingRunner(exitCode = 0, lines = emptyList())
        val editor = FakeDocumentEditor()
        val tempDir = File(folder.root, "work")
        val useCase = useCase(runner, tempDir, editor)
        val fields = setOf(TagField.TITLE, TagField.ARTIST)

        val saved = useCase.execute(inPlaceJob(input, fields)) { _, _ -> }

        assertTrue(runner.args.isEmpty())
        assertEquals(fields, tagStore.fields)
        assertEquals(1, editor.writes.size)
        assertTrue(saved.inPlaceEdit)
        assertEquals("content://doc/song.mp3", saved.uri)
        assertEquals(0, tempDir.listFiles()?.size)
    }

    @Test
    fun `an in-place job with no changed fields leaves the source untouched`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val editor = FakeDocumentEditor()
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), folder.root, editor)

        val saved = useCase.execute(inPlaceJob(input, emptySet())) { _, _ -> }

        assertNull(tagStore.fields)
        assertTrue(editor.writes.isEmpty())
        assertTrue(saved.inPlaceEdit)
    }

    @Test
    fun `a truncated saved copy fails and removes the published row`() = runTest {
        val input = inputFile()
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), File(folder.root, "work"))
        // The runner writes three bytes; the published copy loses one.
        outputStore.publishedBytes = byteArrayOf(9, 9)

        val error = runCatching {
            useCase.execute(job(input)) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertEquals(listOf("content://media/audio/1"), outputStore.deleted)
    }

    @Test
    fun `a mismatched saved read-back removes the published row`() = runTest {
        val input = inputFile()
        val processingJob = job(input)
        tagStore.readBack = processingJob.draft.copy(title = "Something else")
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), File(folder.root, "work"))

        val error = runCatching {
            useCase.execute(processingJob) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertEquals(listOf("content://media/audio/1"), outputStore.deleted)
    }

    @Test
    fun `a failed in-place write restores the original document`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val editor = FakeDocumentEditor().apply { failNextWrite = true }
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), folder.root, editor)

        val error = runCatching {
            useCase.execute(inPlaceJob(input, setOf(TagField.TITLE))) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertEquals(1, editor.writes.size)
        assertArrayEquals(input.readBytes(), editor.writes.single())
    }

    @Test
    fun `a mismatched read-back restores the original document`() = runTest {
        val input = folder.newFile("song.mp3").apply {
            writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        tagStore.readBack = TagDraft(title = "Something else")
        val editor = FakeDocumentEditor()
        val useCase = useCase(WritingRunner(exitCode = 0, lines = emptyList()), folder.root, editor)

        val error = runCatching {
            useCase.execute(inPlaceJob(input, setOf(TagField.TITLE))) { _, _ -> }
        }.exceptionOrNull()

        assertTrue(error is ProcessingException)
        assertEquals(2, editor.writes.size)
        assertArrayEquals(input.readBytes(), editor.writes.last())
    }

    private fun useCase(
        runner: FfmpegRunner,
        tempDir: File,
        editor: DocumentEditor = FakeDocumentEditor(),
    ) = ProcessSongUseCase(
        runner = runner,
        tagStore = tagStore,
        outputStore = outputStore,
        documentEditor = editor,
        tempDirectory = tempDir,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun inputFile(): File = folder.newFile("input.mp4").apply {
        writeBytes(byteArrayOf(0, 1, 2, 3))
    }

    private fun job(input: File, trim: TrimRange? = null): ProcessingJob = job(
        ResolvedInput(
            id = "input",
            uri = "content://input",
            path = input.absolutePath,
            displayName = "input.mp4",
            mimeType = "video/mp4",
            kind = InputKind.VIDEO,
            durationMs = 10_000,
        ),
        trim,
    )

    private fun job(input: ResolvedInput, trim: TrimRange? = null): ProcessingJob = ProcessingJob(
        input = input,
        draft = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
        ),
        trim = trim,
        quality = QualityPreset.V0,
        outputFolder = "Music/Cue",
        filenameTemplate = "{artist} - {title}",
    )

    private fun inPlaceJob(input: File, fields: Set<TagField>): ProcessingJob = job(
        ResolvedInput(
            id = "mp3",
            uri = "content://doc/song.mp3",
            path = input.absolutePath,
            displayName = "song.mp3",
            mimeType = "audio/mpeg",
            kind = InputKind.MP3,
            durationMs = 10_000,
            canEditInPlace = true,
        ),
    ).copy(mode = ProcessingMode.EDIT_IN_PLACE, changedFields = fields)

    private class WritingRunner(
        private val exitCode: Int,
        private val lines: List<String>,
    ) : FfmpegRunner {
        val args = mutableListOf<String>()

        override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int {
            this.args += args
            lines.forEach(onLine)
            File(args.last()).writeBytes(byteArrayOf(9, 9, 9))
            return exitCode
        }
    }

    /** Exits cleanly but leaves no output, like a video whose audio cannot be decoded. */
    private class SilentRunner(private val lines: List<String> = emptyList()) : FfmpegRunner {

        override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int {
            lines.forEach(onLine)
            return 0
        }
    }

    private class RecordingTagStore : TagStore {
        var draft: TagDraft? = null
        var fields: Set<TagField>? = null
        var readBack: TagDraft? = null

        override fun read(file: File): TagDraft? = readBack ?: draft

        override fun writeInPlace(file: File, tag: TagDraft, fields: Set<TagField>) {
            this.draft = tag
            this.fields = fields
        }
    }

    private class FakeDocumentEditor : DocumentEditor {
        private var document: ByteArray = byteArrayOf()

        val writes = mutableListOf<ByteArray>()
        var failNextWrite = false
        var cancelNextRead = false

        override suspend fun replace(uri: String, source: File) {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("provider refused the write")
            }
            val bytes = source.readBytes()
            document = bytes
            writes += bytes
        }

        override suspend fun copyTo(uri: String, target: File) {
            if (cancelNextRead) {
                cancelNextRead = false
                throw CancellationException("cancelled during verification")
            }
            target.writeBytes(document)
        }
    }

    private class RecordingOutputStore : OutputStore {
        var saved = false
        val deleted = mutableListOf<String>()
        val refreshed = mutableListOf<Pair<String, Long?>>()
        private var savedBytes: ByteArray = byteArrayOf()

        /** Set to simulate a copy that lost bytes on the way to MediaStore. */
        var publishedBytes: ByteArray? = null

        override suspend fun save(source: File, displayNameBase: String, outputFolder: String): SavedSong {
            saved = true
            savedBytes = source.readBytes()
            return SavedSong(
                uri = "content://media/audio/1",
                displayName = "$displayNameBase.mp3",
                relativePath = "$outputFolder/",
            )
        }

        override suspend fun copyOut(uri: String, target: File) {
            target.writeBytes(publishedBytes ?: savedBytes)
        }

        override suspend fun delete(uri: String) {
            deleted += uri
        }

        override suspend fun refresh(uri: String, durationMs: Long?) {
            refreshed += uri to durationMs
        }
    }
}
