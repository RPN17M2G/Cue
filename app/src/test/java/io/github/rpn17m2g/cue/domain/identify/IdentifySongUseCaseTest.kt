package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.ocr.DualOcrRecognizer
import io.github.rpn17m2g.cue.domain.ocr.FrameExtractor
import io.github.rpn17m2g.cue.domain.ocr.OcrEngine
import io.github.rpn17m2g.cue.domain.ocr.OcrFrame
import io.github.rpn17m2g.cue.domain.ocr.OcrFrameText
import io.github.rpn17m2g.cue.domain.ocr.OcrLine
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.tags.TagField as StoreField
import io.github.rpn17m2g.cue.tags.TagStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IdentifySongUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settings = AppSettings()

    @Test
    fun `video OCR feeds the cascade and removes its frames`() = runTest {
        val extractor = FakeFrameExtractor()
        val useCase = useCase(
            extractor = extractor,
            latinLines = listOf("Daft Punk - Get Lucky"),
        )
        var lookupCalls = 0
        val cascade = cascade {
            lookupCalls += 1
            listOf(
                MetadataMatch(
                    title = "Get Lucky",
                    artist = "Daft Punk",
                    album = "Random Access Memories",
                    source = IdentificationSource.ITUNES,
                    score = 1.0,
                ),
            )
        }

        val result = useCase.identify(
            input = videoInput(),
            embeddedTags = null,
            settings = settings,
            preferHebrew = false,
            cascade = cascade,
        )

        assertEquals(1, lookupCalls)
        assertEquals("Get Lucky", result.outcome.tag.title)
        assertEquals("Daft Punk", result.outcome.tag.artist)
        assertEquals("Random Access Memories", result.outcome.tag.album)
        assertFalse(result.ocrFailed)
        assertFalse(extractor.lastOutput!!.exists())
    }

    @Test
    fun `a frame extraction failure still returns an editable result`() = runTest {
        val extractor = FakeFrameExtractor(failure = IllegalStateException("decode"))
        val useCase = useCase(extractor = extractor)
        var lookupCalls = 0
        val cascade = cascade {
            lookupCalls += 1
            emptyList()
        }

        val result = useCase.identify(
            input = videoInput(),
            embeddedTags = null,
            settings = settings,
            preferHebrew = false,
            cascade = cascade,
        )

        assertEquals(0, lookupCalls)
        assertTrue(result.ocrFailed)
        assertTrue(result.outcome.tag.title.isBlank())
        assertFalse(extractor.lastOutput!!.exists())
    }

    @Test
    fun `an MP3 with complete embedded tags skips frame extraction`() = runTest {
        val extractor = FakeFrameExtractor(failure = IllegalStateException("must not run"))
        val useCase = useCase(extractor = extractor)
        val embedded = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
        )

        val result = useCase.identify(
            input = mp3Input(),
            embeddedTags = embedded,
            settings = settings,
            preferHebrew = false,
            cascade = cascade { emptyList() },
        )

        assertEquals(0, extractor.calls)
        assertTrue(result.outcome.usedEmbeddedTags)
        assertEquals("Random Access Memories", result.outcome.tag.album)
    }

    @Test
    fun `embedded tags are read when the caller has none`() = runTest {
        val embedded = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
        )
        val useCase = useCase(
            extractor = FakeFrameExtractor(),
            tagStore = FakeTagStore(embedded),
        )

        val result = useCase.identify(
            input = mp3Input(),
            embeddedTags = null,
            settings = settings,
            preferHebrew = false,
            cascade = cascade { emptyList() },
        )

        assertTrue(result.outcome.usedEmbeddedTags)
        assertEquals("Get Lucky", result.outcome.tag.title)
    }

    @Test
    fun `the network toggle prevents the online lookup`() = runTest {
        val useCase = useCase(
            extractor = FakeFrameExtractor(),
            latinLines = listOf("Daft Punk - Get Lucky"),
        )
        var lookupCalls = 0
        val cascade = cascade {
            lookupCalls += 1
            emptyList()
        }

        val result = useCase.identify(
            input = videoInput(),
            embeddedTags = null,
            settings = settings.copy(networkLookups = false),
            preferHebrew = false,
            cascade = cascade,
        )

        assertEquals(0, lookupCalls)
        assertEquals("Get Lucky", result.outcome.tag.title)
        assertEquals(
            IdentificationSource.OCR_LATIN,
            result.outcome.sources[TagField.TITLE],
        )
    }

    @Test
    fun `a Hebrew UI records the Hebrew OCR source`() = runTest {
        val useCase = useCase(
            extractor = FakeFrameExtractor(),
            latinLines = listOf("Daft Punk - Get Lucky"),
            hebrewLines = listOf("דפט פאנק - גט לאקי"),
        )

        val result = useCase.identify(
            input = videoInput(),
            embeddedTags = null,
            settings = settings.copy(networkLookups = false),
            preferHebrew = true,
            cascade = cascade { emptyList() },
        )

        assertEquals("גט לאקי", result.outcome.tag.title)
        assertTrue(result.hebrewOcrUsed)
        assertEquals(
            IdentificationSource.OCR_HEBREW,
            result.outcome.sources[TagField.TITLE],
        )
    }

    private fun useCase(
        extractor: FakeFrameExtractor,
        latinLines: List<String> = listOf("Daft Punk - Get Lucky"),
        hebrewLines: List<String> = emptyList(),
        tagStore: TagStore = FakeTagStore(null),
    ): IdentifySongUseCase = IdentifySongUseCase(
        tagStore = tagStore,
        frameExtractor = extractor,
        recognizer = DualOcrRecognizer(
            latin = FakeOcrEngine(IdentificationSource.OCR_LATIN, latinLines),
            hebrew = FakeOcrEngine(IdentificationSource.OCR_HEBREW, hebrewLines),
        ),
        framesRoot = temporaryFolder.newFolder(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun cascade(lookup: suspend (LookupSeed) -> List<MetadataMatch>) =
        IdentificationCascade(
            lookup = lookup,
            fingerprintLookup = { emptyList() },
        )

    private fun videoInput(): ResolvedInput = ResolvedInput(
        id = "video",
        uri = "content://video",
        path = temporaryFolder.newFile("recording.mp4").absolutePath,
        displayName = "recording.mp4",
        mimeType = "video/mp4",
        kind = InputKind.VIDEO,
        durationMs = 30_000L,
    )

    private fun mp3Input(): ResolvedInput = ResolvedInput(
        id = "mp3",
        uri = "content://mp3",
        path = temporaryFolder.newFile("song.mp3").absolutePath,
        displayName = "song.mp3",
        mimeType = "audio/mpeg",
        kind = InputKind.MP3,
        durationMs = 180_000L,
    )

    private class FakeFrameExtractor(
        private val failure: Exception? = null,
    ) : FrameExtractor {
        var calls: Int = 0
            private set
        var lastOutput: File? = null
            private set

        override suspend fun extract(
            video: File,
            timestampsMs: List<Long>,
            outputDir: File,
        ): List<OcrFrame> {
            calls += 1
            lastOutput = outputDir
            failure?.let { throw it }
            outputDir.mkdirs()
            return timestampsMs.map { timestamp ->
                val file = File(outputDir, "frame-$timestamp.jpg")
                file.writeText("frame")
                OcrFrame(timestamp, file)
            }
        }
    }

    private class FakeOcrEngine(
        override val source: IdentificationSource,
        private val lines: List<String>,
    ) : OcrEngine {
        override suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText> =
            frames.map { frame -> OcrFrameText(frame.timestampMs, lines.map(::OcrLine)) }
    }

    private class FakeTagStore(private val tag: TagDraft?) : TagStore {
        override fun read(file: File): TagDraft? = tag

        override fun writeInPlace(file: File, tag: TagDraft, fields: Set<StoreField>) {
            error("not used")
        }
    }
}

