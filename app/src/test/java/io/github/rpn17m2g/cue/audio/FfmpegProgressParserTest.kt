package io.github.rpn17m2g.cue.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegProgressParserTest {

    @Test
    fun `parses time and reports the fraction of the known duration`() {
        val parser = FfmpegProgressParser(totalDurationMs = 20_000)

        val state = parser.update("frame= 42 fps=0.0 q=0.0 size=  128kB time=00:00:10.00 bitrate= 104.9kbits/s")

        assertEquals(10_000L, state.positionMs)
        assertEquals(0.5f, state.progress, 0.0001f)
        assertFalse(state.isComplete)
        assertNull(state.fatalError)
        assertTrue(state.lastErrorLines.isEmpty())
    }

    @Test
    fun `parses out_time_ms as microseconds like real ffmpeg progress output`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val state = parser.update("out_time_ms=2500000")

        assertEquals(2_500L, state.positionMs)
        assertEquals(0.25f, state.progress, 0.0001f)
    }

    @Test
    fun `parses out_time_us as microseconds`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val state = parser.update("out_time_us=2500000")

        assertEquals(2_500L, state.positionMs)
    }

    @Test
    fun `prefers the clock form when a line carries several timestamps`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val state = parser.update("out_time_ms=9000000 time=00:00:02.00")

        assertEquals(2_000L, state.positionMs)
    }

    @Test
    fun `handles carriage return separated progress updates in one chunk`() {
        val parser = FfmpegProgressParser(totalDurationMs = 4_000)

        val state = parser.update(
            "frame=1 time=00:00:01.00\rframe=2 time=00:00:02.00\rframe=3 time=00:00:03.00\r",
        )

        assertEquals(3_000L, state.positionMs)
        assertEquals(0.75f, state.progress, 0.0001f)
    }

    @Test
    fun `never moves backwards when updates arrive out of order`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        parser.update("time=00:00:05.00")
        val state = parser.update("time=00:00:03.00")

        assertEquals(5_000L, state.positionMs)
        assertEquals(0.5f, state.progress, 0.0001f)
    }

    @Test
    fun `clamps progress at one when ffmpeg reports past the duration`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val state = parser.update("time=00:00:15.00")

        assertEquals(1.0f, state.progress, 0.0001f)
    }

    @Test
    fun `reports zero progress when the duration is unknown but still tracks position`() {
        val parser = FfmpegProgressParser(totalDurationMs = null)

        val state = parser.update("time=00:00:07.50")

        assertEquals(7_500L, state.positionMs)
        assertEquals(0.0f, state.progress, 0.0001f)
    }

    @Test
    fun `reports zero progress for a non-positive duration`() {
        val zero = FfmpegProgressParser(totalDurationMs = 0).update("time=00:00:07.50")
        val negative = FfmpegProgressParser(totalDurationMs = -1).update("time=00:00:07.50")

        assertEquals(0.0f, zero.progress, 0.0001f)
        assertEquals(0.0f, negative.progress, 0.0001f)
    }

    @Test
    fun `ignores malformed and unrelated lines`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        parser.update("time=not-a-clock")
        parser.update("Duration: 00:00:42.00")
        parser.update("Duration: N/A, start: 0.000000, bitrate: N/A")
        parser.update("size=N/A time=N/A bitrate=N/A")
        parser.update("")
        val state = parser.update("frame= 7 fps=0.0 q=0.0")

        assertNull(state.positionMs)
        assertEquals(0.0f, state.progress, 0.0001f)
        assertNull(state.fatalError)
    }

    @Test
    fun `parses fractional seconds with one two and three digits`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        assertEquals(500L, parser.update("time=00:00:00.5").positionMs)
        assertEquals(1_230L, parser.update("time=00:00:01.23").positionMs)
        assertEquals(2_456L, parser.update("time=00:00:02.456").positionMs)
    }

    @Test
    fun `ignores negative timestamps that ffmpeg reports before the stream starts`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val negative = parser.update(
            "frame= 120 fps= 30 q=-1.0 size= 512kB time=-577014:32:22.77 bitrate=N/A speed=N/A",
        )

        assertNull(negative.positionMs)
        assertEquals(0.0f, negative.progress, 0.0001f)

        val recovered = parser.update("time=00:00:02.00")
        assertEquals(2_000L, recovered.positionMs)
        assertEquals(0.2f, recovered.progress, 0.0001f)
    }

    @Test
    fun `detects progress end and completes at the total duration`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        parser.update("time=00:00:09.80")
        val state = parser.update("progress=end")

        assertTrue(state.isComplete)
        assertEquals(10_000L, state.positionMs)
        assertEquals(1.0f, state.progress, 0.0001f)
    }

    @Test
    fun `progress end completes even when the duration is unknown`() {
        val parser = FfmpegProgressParser(totalDurationMs = null)

        val state = parser.update("progress=end")

        assertTrue(state.isComplete)
        assertEquals(0.0f, state.progress, 0.0001f)
    }

    @Test
    fun `latches only authoritative fatal lines`() {
        listOf(
            "Conversion failed!",
            "Error opening input: No such file or directory",
            "Invalid data found when processing input",
            "No such file or directory",
        ).forEach { line ->
            val state = FfmpegProgressParser(totalDurationMs = 10_000).update(line)

            assertEquals(line, state.fatalError)
        }
    }

    @Test
    fun `error while decoding is a diagnostic only because the exit code decides failure`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)
        val line = "Error while decoding stream #0:0: Invalid data found when processing input"

        val state = parser.update(line)

        assertNull(state.fatalError)
        assertEquals(listOf(line), state.lastErrorLines)
    }

    @Test
    fun `outcome is successful when the exit code is zero despite decoding diagnostics`() = runBlocking {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)
        val runner = FakeFfmpegRunner(
            exitCode = 0,
            lines = listOf("time=00:00:02.00", "Error while decoding stream #0:0"),
        )

        val exitCode = runner.run(emptyList()) { parser.update(it) }
        val outcome = parser.state.toOutcome(exitCode)

        assertFalse(outcome.failed)
        assertEquals(0, outcome.exitCode)
        assertEquals(0.2f, outcome.progress, 0.0001f)
        assertEquals(listOf("Error while decoding stream #0:0"), outcome.diagnostics)
        assertNull(parser.state.fatalError)
    }

    @Test
    fun `outcome fails when the exit code is non-zero`() = runBlocking {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)
        val runner = FakeFfmpegRunner(exitCode = 1, lines = listOf("Conversion failed!"))

        val exitCode = runner.run(emptyList()) { parser.update(it) }
        val outcome = parser.state.toOutcome(exitCode)

        assertTrue(outcome.failed)
        assertEquals(1, outcome.exitCode)
        assertEquals(listOf("Conversion failed!"), outcome.diagnostics)
        assertEquals("Conversion failed!", parser.state.fatalError)
    }

    @Test
    fun `strips ffmpeg log prefixes before classifying errors`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val diagnostic = parser.update("[ffmpeg @ 0x55d0] Error while decoding stream #0:0")

        assertNull(diagnostic.fatalError)
        assertEquals(listOf("Error while decoding stream #0:0"), diagnostic.lastErrorLines)

        val fatal = parser.update("[in#0 @ 0x55d0] Conversion failed!")

        assertEquals("Conversion failed!", fatal.fatalError)
        assertEquals(
            listOf("Error while decoding stream #0:0", "Conversion failed!"),
            fatal.lastErrorLines,
        )
    }

    @Test
    fun `could not find codec parameters is a diagnostic rather than a fatal line`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        val state = parser.update("Could not find codec parameters for stream 0")

        assertNull(state.fatalError)
        assertEquals(1, state.lastErrorLines.size)
    }

    @Test
    fun `does not treat ordinary error words as fatal`() {
        val state = FfmpegProgressParser(totalDurationMs = 10_000)
            .update("frame=10 error concealment applied while decoding")

        assertNull(state.fatalError)
        assertTrue(state.lastErrorLines.isEmpty())
    }

    @Test
    fun `keeps the first fatal error while continuing to track progress`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        parser.update("Error opening input")
        val state = parser.update("Conversion failed!")

        assertEquals("Error opening input", state.fatalError)
    }

    @Test
    fun `keeps only the most recent diagnostics`() {
        val parser = FfmpegProgressParser(totalDurationMs = 10_000)

        repeat(25) { parser.update("Error number $it while decoding") }

        val errors = parser.state.lastErrorLines
        assertEquals(20, errors.size)
        assertEquals("Error number 5 while decoding", errors.first())
        assertEquals("Error number 24 while decoding", errors.last())
    }

    @Test
    fun `exposes the latest state after several updates`() {
        val parser = FfmpegProgressParser(totalDurationMs = 2_000)

        parser.update("time=00:00:01.00")
        parser.update("time=00:00:01.50")

        assertEquals(1_500L, parser.state.positionMs)
        assertEquals(0.75f, parser.state.progress, 0.0001f)
    }

    @Test
    fun `computes progress for a duration supplied later`() {
        val parser = FfmpegProgressParser(totalDurationMs = null)
        parser.update("time=00:00:05.00")

        assertEquals(0.5f, parser.progress(totalDurationMs = 10_000), 0.0001f)
    }
}
