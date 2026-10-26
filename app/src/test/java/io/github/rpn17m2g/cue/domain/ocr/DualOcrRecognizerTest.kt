package io.github.rpn17m2g.cue.domain.ocr

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualOcrRecognizerTest {

    private val frames = listOf(
        OcrFrame(0L, File("frame-0.jpg")),
        OcrFrame(2_000L, File("frame-2000.jpg")),
    )

    @Test
    fun `latin reading is used without a Hebrew pass when it is plausible`() = runTest {
        val hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, listOf(listOf("דפט פאנק - גט לאקי")))
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = hebrew,
        ).recognize(frames, preferHebrew = false)

        assertEquals(0, hebrew.calls)
        assertEquals("Get Lucky", recognition.candidates.first().title)
        assertFalse(recognition.hebrewUsed)
        assertFalse(recognition.failed)
    }

    @Test
    fun `a Hebrew UI runs the Hebrew pass and prefers its reading`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                repeated("דפט פאנק - גט לאקי"),
            ),
        ).recognize(frames, preferHebrew = true)

        assertEquals("גט לאקי", recognition.candidates.first().title)
        assertEquals("דפט פאנק", recognition.candidates.first().artist)
        assertTrue(recognition.hebrewUsed)
    }

    @Test
    fun `an implausible Latin reading falls back to Hebrew`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, listOf(emptyList())),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                repeated("דפט פאנק - גט לאקי"),
            ),
        ).recognize(frames, preferHebrew = false)

        assertEquals("גט לאקי", recognition.candidates.first().title)
        assertTrue(recognition.hebrewUsed)
    }

    @Test
    fun `a failing Hebrew engine leaves the Latin result in place`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                emptyList(),
                failure = IllegalStateException("no traineddata"),
            ),
        ).recognize(frames, preferHebrew = true)

        assertEquals("Get Lucky", recognition.candidates.first().title)
        assertFalse(recognition.hebrewUsed)
        assertFalse(recognition.failed)
    }

    @Test
    fun `a failing Latin engine falls back to Hebrew`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(
                IdentificationSource.OCR_LATIN,
                emptyList(),
                failure = IllegalStateException("ML Kit unavailable"),
            ),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                repeated("דפט פאנק - גט לאקי"),
            ),
        ).recognize(frames, preferHebrew = false)

        assertEquals("גט לאקי", recognition.candidates.first().title)
        assertTrue(recognition.hebrewUsed)
        assertFalse(recognition.failed)
    }

    @Test
    fun `both engines failing reports a failed recognition`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(
                IdentificationSource.OCR_LATIN,
                emptyList(),
                failure = IllegalStateException("latin"),
            ),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                emptyList(),
                failure = IllegalStateException("hebrew"),
            ),
        ).recognize(frames, preferHebrew = false)

        assertTrue(recognition.candidates.isEmpty())
        assertTrue(recognition.failed)
    }

    @Test
    fun `raw lines are deduplicated across frames`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, emptyList()),
        ).recognize(frames, preferHebrew = false)

        assertEquals(listOf("Daft Punk - Get Lucky"), recognition.rawLines)
    }

    private fun repeated(line: String): List<List<String>> =
        frames.map { listOf(line) }

    private class FakeEngine(
        override val source: IdentificationSource,
        private val lines: List<List<String>>,
        private val failure: Exception? = null,
    ) : OcrEngine {
        var calls: Int = 0
            private set

        override suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText> {
            calls += 1
            failure?.let { throw it }
            return frames.mapIndexed { index, frame ->
                OcrFrameText(frame.timestampMs, lines.getOrElse(index) { emptyList() })
            }
        }
    }
}
