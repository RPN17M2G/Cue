package io.github.rpn17m2g.cue.processing

import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpatric.mp3agic.Mp3File
import io.github.rpn17m2g.cue.CueApplication
import io.github.rpn17m2g.cue.data.input.DocumentEditor
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange
import io.github.rpn17m2g.cue.domain.model.parseLrc
import io.github.rpn17m2g.cue.domain.model.shiftedTo
import io.github.rpn17m2g.cue.domain.model.toLrc
import io.github.rpn17m2g.cue.tags.TagField
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pipeline against the real FFmpeg binary, the real mp3agic store and a real
 * file on the device. Unit tests replace all three; this is what proves the
 * native binary runs on the target ABI and that the frames leaving the app are
 * readable by another caller. Fixtures are generated in the test, so a run needs
 * no media on the device and stays short.
 */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {

    private val container
        get() = ApplicationProvider.getApplicationContext<CueApplication>().container

    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = File(container.appContext.cacheDir, "pipeline-${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `converts a fixture and writes tags the reader can verify`() = runBlocking {
        val input = writeSilence(File(folder, "input.mp3"), 6_000)
        val output = File(folder, "out/result.mp3")
        val store = FileOutputStore(output)
        val job = job(
            input = input,
            kind = InputKind.MP3,
            durationMs = 6_000,
            draft = sampleDraft(),
            trim = TrimRange(2_000, 5_000),
        )

        val saved = useCase(store).execute(job) { _, _ -> }

        assertTrue(output.isFile)
        assertEquals(3_000L, saved.durationMs)
        assertDuration(output, 3_000)
        val read = checkNotNull(container.tagStore.read(output))
        assertEquals(SAMPLE_TITLE, read.title)
        assertEquals(SAMPLE_ARTIST, read.artist)
        assertEquals(SAMPLE_LYRICS, read.lyrics)
        assertEquals(SAMPLE_LRC, read.syncedLyrics)
        assertNotNull("the SYLT frame must reach the published file", syltFrame(output))
    }

    @Test
    fun `trims a library file in place and keeps its tags`() = runBlocking {
        val library = File(folder, "library.mp3")
        val store = FileOutputStore(library)
        val full = sampleDraft()
        useCase(store).execute(
            job(
                input = writeSilence(File(folder, "input.mp3"), 6_000),
                kind = InputKind.MP3,
                durationMs = 6_000,
                draft = full,
                trim = null,
            ),
        ) { _, _ -> }
        assertDuration(library, 6_000)
        val originalSize = library.length()

        // The resolver keeps a cache copy of the document; the job replaces the
        // document at the input URI, which is the library file here.
        val cacheCopy = File(folder, "input-copy.mp3")
        library.copyTo(cacheCopy, overwrite = true)
        val trim = TrimRange(1_000, 4_000)
        val job = job(
            input = cacheCopy,
            kind = InputKind.MP3,
            durationMs = null,
            draft = full.copy(
                syncedLyrics = parseLrc(SAMPLE_LRC).shiftedTo(trim.startMs, trim.endMs).toLrc(),
            ),
            trim = trim,
        ).copy(
            mode = ProcessingMode.TRIM_IN_PLACE,
            changedFields = emptySet(),
        )

        val saved = useCase(store, FileDocumentEditor(library)).execute(job) { _, _ -> }

        assertTrue(saved.inPlaceEdit)
        assertTrue(saved.trimmedInPlace)
        assertTrue(library.length() < originalSize)
        assertDuration(library, 3_000)
        assertTrue("the cache copy is the restore point", cacheCopy.isFile)
        val read = checkNotNull(container.tagStore.read(library))
        assertEquals(SAMPLE_TITLE, read.title)
        assertEquals(SAMPLE_LYRICS, read.lyrics)
        assertEquals(job.draft.syncedLyrics, read.syncedLyrics)
        assertNotNull(syltFrame(library))
    }

    @Test
    fun `rewrites tags in place without touching the audio`() = runBlocking {
        val library = File(folder, "library.mp3")
        val store = FileOutputStore(library)
        useCase(store).execute(
            job(
                input = writeSilence(File(folder, "input.mp3"), 6_000),
                kind = InputKind.MP3,
                durationMs = 6_000,
                draft = sampleDraft(),
                trim = null,
            ),
        ) { _, _ -> }
        val cacheCopy = File(folder, "input-copy.mp3")
        library.copyTo(cacheCopy, overwrite = true)

        // Album and lyrics are deliberately changed too, but left out of
        // changedFields: a writer that ignored the field set would overwrite
        // them, and the assertions below would catch it.
        val edited = sampleDraft().copy(
            title = "Edited title",
            artist = "Another artist",
            album = "A different album",
            lyrics = "Different words",
            syncedLyrics = "[00:00.00]Different words",
        )
        val job = job(
            input = cacheCopy,
            kind = InputKind.MP3,
            durationMs = 6_000,
            draft = edited,
            trim = null,
        ).copy(
            mode = ProcessingMode.EDIT_IN_PLACE,
            changedFields = setOf(TagField.TITLE, TagField.ARTIST),
        )

        val saved = useCase(store, FileDocumentEditor(library)).execute(job) { _, _ -> }

        assertTrue(saved.inPlaceEdit)
        assertFalse(saved.trimmedInPlace)
        // No re-encode, so the audio was not cut or re-encoded.
        assertDuration(library, 6_000)
        val read = checkNotNull(container.tagStore.read(library))
        assertEquals("Edited title", read.title)
        assertEquals("Another artist", read.artist)
        // Fields outside changedFields keep whatever the file already carried.
        assertEquals(SAMPLE_ALBUM, read.album)
        assertEquals(SAMPLE_LYRICS, read.lyrics)
        assertEquals(SAMPLE_LRC, read.syncedLyrics)
    }

    private fun useCase(
        store: OutputStore,
        // Only in-place jobs touch this; the conversion tests pass a store that
        // writes somewhere else. It names a file, never the folder itself.
        editor: DocumentEditor = FileDocumentEditor(File(folder, "document.mp3")),
    ) = ProcessSongUseCase(
        runner = container.ffmpegRunner,
        tagStore = container.tagStore,
        outputStore = store,
        documentEditor = editor,
        tempDirectory = folder,
    )

    private fun job(
        input: File,
        kind: InputKind,
        durationMs: Long?,
        draft: TagDraft,
        trim: TrimRange?,
    ) = ProcessingJob(
        input = ResolvedInput(
            id = "instrumented",
            uri = "file://${input.absolutePath}",
            path = input.absolutePath,
            displayName = input.name,
            mimeType = if (kind == InputKind.MP3) "audio/mpeg" else "video/mp4",
            kind = kind,
            durationMs = durationMs,
            canEditInPlace = kind == InputKind.MP3,
        ),
        draft = draft,
        trim = trim,
        quality = QualityPreset.V2,
        outputFolder = "Music/Cue",
        filenameTemplate = "{artist} - {title}",
    )

    /**
     * A silent MPEG-1 Layer III stream: 32 kbps, 44.1 kHz, mono, no CRC, no
     * padding, so every frame is [FRAME_BYTES] long and holds 1152 samples. A
     * zeroed frame body is a valid frame that decodes to silence.
     *
     * The packaged FFmpeg is a minimal build (`--enable-demuxer=mov,mp3,...`,
     * `--enable-decoder=aac,mp3,...`), so a generated WAV cannot be used as the
     * fixture: there is no WAV demuxer and no PCM decoder. MP3 is the one format
     * the app both accepts and can produce, and this stream needs no encoder.
     */
    private fun writeSilence(file: File, durationMs: Long): File {
        val frames = ceil(durationMs / FRAME_DURATION_MS).toInt()
        val bytes = ByteArray(frames * FRAME_BYTES)
        repeat(frames) { frame ->
            val offset = frame * FRAME_BYTES
            bytes[offset] = 0xFF.toByte()
            bytes[offset + 1] = 0xFB.toByte()
            bytes[offset + 2] = 0x10.toByte()
            bytes[offset + 3] = 0xC0.toByte()
        }
        file.writeBytes(bytes)
        return file
    }

    private fun sampleDraft() = TagDraft(
        title = SAMPLE_TITLE,
        artist = SAMPLE_ARTIST,
        album = SAMPLE_ALBUM,
        lyrics = SAMPLE_LYRICS,
        syncedLyrics = SAMPLE_LRC,
    )

    private fun syltFrame(file: File): String? =
        Mp3File(file).id3v2Tag?.frameSets?.get("SYLT")?.frames?.firstOrNull()?.id

    private fun assertDuration(file: File, expectedMs: Long) {
        val actual = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        }
        // MP3 frames are ~26 ms; a decoder may also pad the end slightly.
        assertTrue(
            "duration was $actual ms, expected about $expectedMs ms",
            abs(actual - expectedMs) <= 1_200L,
        )
    }

    private class FileOutputStore(private val destination: File) : OutputStore {

        override suspend fun save(
            source: File,
            displayNameBase: String,
            outputFolder: String,
        ): SavedSong {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            return SavedSong(
                uri = "file://${destination.absolutePath}",
                displayName = "$displayNameBase.mp3",
                relativePath = "$outputFolder/",
            )
        }

        override suspend fun copyOut(uri: String, target: File) {
            destination.copyTo(target, overwrite = true)
        }

        override suspend fun delete(uri: String) {
            destination.delete()
        }
    }

    /** Stands in for the document at a URI: here the file is the library copy. */
    private class FileDocumentEditor(private val target: File) : DocumentEditor {

        override suspend fun replace(uri: String, source: File) {
            source.copyTo(target, overwrite = true)
        }

        override suspend fun copyTo(uri: String, targetFile: File) {
            target.copyTo(targetFile, overwrite = true)
        }
    }

    private companion object {
        const val SAMPLE_TITLE = "favorite crime"
        const val SAMPLE_ARTIST = "Olivia Rodrigo"
        const val SAMPLE_ALBUM = "SOUR"
        const val SAMPLE_LYRICS = "Know that I loved you so bad\nI was your willin' accomplice"
        const val SAMPLE_LRC = "[00:00.20]Know that I loved you so bad\n[00:02.50]I was your willin' accomplice"

        /** 144 * 32_000 / 44_100, floored: the CBR frame size at 32 kbps. */
        const val FRAME_BYTES = 104

        /** 1152 samples at 44.1 kHz. */
        const val FRAME_DURATION_MS = 1_152.0 / 44_100.0 * 1_000.0
    }
}
