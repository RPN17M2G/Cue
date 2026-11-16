package io.github.rpn17m2g.cue.domain.ocr

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
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
    fun `a Hebrew reading wins on a Hebrew screen even in an English app`() = runTest {
        val hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, listOf(listOf(OcrLine("דפט פאנק - גט לאקי"))))
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Order now")),
            hebrew = hebrew,
        ).recognize(frames, preferHebrew = false)

        assertEquals(1, hebrew.calls)
        assertEquals("גט לאקי", recognition.candidates.first().title)
        assertTrue(recognition.hebrewUsed)
        assertFalse(recognition.failed)
    }

    @Test
    fun `Latin wins when the Hebrew pass read no Hebrew at all`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, listOf(emptyList(), emptyList())),
        ).recognize(frames, preferHebrew = false)

        assertEquals("Get Lucky", recognition.candidates.first().title)
        assertFalse(recognition.hebrewUsed)
    }

    @Test
    fun `Hebrew gibberish over an English screen never beats the Latin reading`() = runTest {
        // What Tesseract actually returned for the two English fixtures. Every
        // token has a digit or a symbol glued in, and it scored as high as the
        // correct Latin reading.
        val gibberish = listOf(
            "זכ6חוסהּוח! - 6צוסטם",
            "סוחוז06)וזסעה+",
        )
        for (hebrewGibberish in gibberish) {
            for (preferHebrew in listOf(false, true)) {
                val recognition = DualOcrRecognizer(
                    latin = FakeEngine(
                        IdentificationSource.OCR_LATIN,
                        repeated("Imagine Dragons - Believer"),
                    ),
                    hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, repeated(hebrewGibberish)),
                ).recognize(frames, preferHebrew = preferHebrew)

                assertEquals("Believer", recognition.candidates.first().title)
                assertFalse(recognition.hebrewUsed)
            }
        }
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
    fun `a Hebrew UI keeps a clearly better Latin reading`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                listOf(listOf(OcrLine("גט לאקי")), emptyList()),
            ),
        ).recognize(frames, preferHebrew = true)

        assertEquals("Get Lucky", recognition.candidates.first().title)
        assertFalse(recognition.hebrewUsed)
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

    @Test
    fun `raw lines come from the winning pass only`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Believer")),
            hebrew = FakeEngine(
                IdentificationSource.OCR_HEBREW,
                listOf(listOf(OcrLine("6אוצה וסצוסא")), emptyList()),
            ),
        ).recognize(frames, preferHebrew = false)

        assertEquals(listOf("Believer"), recognition.rawLines)
    }

    @Test
    fun `the two passes run concurrently`() = runTest {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        fun engine(source: IdentificationSource): OcrEngine = object : OcrEngine {
            override val source: IdentificationSource = source

            override suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText> {
                val running = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, running) }
                delay(50)
                active.decrementAndGet()
                return frames.map { frame ->
                    OcrFrameText(frame.timestampMs, listOf(OcrLine("Daft Punk - Get Lucky")))
                }
            }
        }

        DualOcrRecognizer(
            latin = engine(IdentificationSource.OCR_LATIN),
            hebrew = engine(IdentificationSource.OCR_HEBREW),
        ).recognize(frames, preferHebrew = false)

        assertEquals(2, peak.get())
    }

    @Test
    fun `both passes always run even when the first one is plausible`() = runTest {
        val latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky"))
        val hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, repeated("דפט פאנק - גט לאקי"))

        DualOcrRecognizer(latin = latin, hebrew = hebrew).recognize(frames, preferHebrew = false)

        assertEquals(1, latin.calls)
        assertEquals(1, hebrew.calls)
    }

    @Test
    fun `a forced Latin scan ignores a Hebrew reading`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, repeated("דפט פאנק - גט לאקי")),
        ).recognize(frames, preferHebrew = false, script = OcrScript.LATIN)

        assertEquals("Get Lucky", recognition.candidates.first().title)
        assertFalse(recognition.hebrewUsed)
    }

    @Test
    fun `a forced Hebrew scan ignores a Latin reading`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, repeated("דפט פאנק - גט לאקי")),
        ).recognize(frames, preferHebrew = false, script = OcrScript.HEBREW)

        assertEquals("גט לאקי", recognition.candidates.first().title)
        assertTrue(recognition.hebrewUsed)
    }

    @Test
    fun `a forced Hebrew scan reports an empty read when the Hebrew engine read nothing`() = runTest {
        val recognition = DualOcrRecognizer(
            latin = FakeEngine(IdentificationSource.OCR_LATIN, repeated("Daft Punk - Get Lucky")),
            hebrew = FakeEngine(IdentificationSource.OCR_HEBREW, listOf(emptyList(), emptyList())),
        ).recognize(frames, preferHebrew = false, script = OcrScript.HEBREW)

        assertTrue(recognition.candidates.isEmpty())
        assertFalse(recognition.failed)
    }

    private fun repeated(line: String): List<List<OcrLine>> =
        frames.map { listOf(OcrLine(line)) }

    private class FakeEngine(
        override val source: IdentificationSource,
        private val lines: List<List<OcrLine>>,
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


