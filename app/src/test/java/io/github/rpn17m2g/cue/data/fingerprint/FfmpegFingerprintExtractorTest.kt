package io.github.rpn17m2g.cue.data.fingerprint

import io.github.rpn17m2g.cue.audio.FfmpegRunner
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FfmpegFingerprintExtractorTest {

    private val root = File("build/tmp/fingerprint-extractor")
    private val outputDirectory = File(root, "out")
    private val inputDirectory = File(root, "in")

    @Before
    fun resetDirectories() {
        root.deleteRecursively()
        outputDirectory.mkdirs()
        inputDirectory.mkdirs()
    }

    @Test
    fun `writes a base64 fingerprint for the standard slice and reports its duration`() = runTest {
        val runner = WritingRunner(content = FINGERPRINT)
        val input = inputFile()

        val result = extractor(runner).extract(input, clipDurationMs = 240_000)

        assertEquals(FINGERPRINT, result?.value)
        assertEquals(30, result?.durationSeconds)
        val args = runner.invocations.single()
        assertTrue(args.containsAll(listOf("-ss", "20.000", "-t", "30.000")))
        assertTrue(args.containsAll(listOf("-f", "chromaprint", "-fp_format", "base64")))
        assertEquals(emptyList<String>(), outputDirectory.list()?.toList())
    }

    @Test
    fun `short clips are fingerprinted from the start with the clip duration`() = runTest {
        val runner = WritingRunner(content = FINGERPRINT)

        val result = extractor(runner).extract(inputFile(), clipDurationMs = 45_000)

        assertEquals(45, result?.durationSeconds)
        val args = runner.invocations.single()
        assertTrue(args.containsAll(listOf("-ss", "0.000")))
        assertTrue(!args.contains("-t"))
    }

    @Test
    fun `unknown duration gets the standard slice duration`() = runTest {
        val runner = WritingRunner(content = FINGERPRINT)

        val result = extractor(runner).extract(inputFile(), clipDurationMs = null)

        assertEquals(30, result?.durationSeconds)
    }

    @Test
    fun `clips shorter than a second are not fingerprinted`() = runTest {
        val runner = WritingRunner(content = FINGERPRINT)

        val result = extractor(runner).extract(inputFile(), clipDurationMs = 900)

        assertNull(result)
        assertTrue(runner.invocations.isEmpty())
    }

    @Test
    fun `durations are floored to whole seconds`() {
        assertEquals(45, FfmpegFingerprintExtractor.fingerprintDurationSeconds(45_900))
        assertEquals(30, FfmpegFingerprintExtractor.fingerprintDurationSeconds(120_000))
        assertEquals(30, FfmpegFingerprintExtractor.fingerprintDurationSeconds(50_000))
    }

    @Test
    fun `a failed ffmpeg run yields no fingerprint and leaves no file behind`() = runTest {
        val runner = WritingRunner(exitCode = 1, content = FINGERPRINT)

        val result = extractor(runner).extract(inputFile(), clipDurationMs = 120_000)

        assertNull(result)
        assertEquals(emptyList<String>(), outputDirectory.list()?.toList())
    }

    @Test
    fun `empty or garbled output yields no fingerprint`() = runTest {
        assertNull(extractor(WritingRunner(content = "")).extract(inputFile(), 120_000))
        assertNull(extractor(WritingRunner(content = "not a fingerprint!")).extract(inputFile(), 120_000))
        assertNull(extractor(WritingRunner(content = null)).extract(inputFile(), 120_000))
    }

    @Test
    fun `runner failures degrade to no fingerprint`() = runTest {
        val runner = WritingRunner(failure = IllegalStateException("binary missing"))

        val result = extractor(runner).extract(inputFile(), clipDurationMs = 120_000)

        assertNull(result)
    }

    private fun extractor(runner: FfmpegRunner) =
        FfmpegFingerprintExtractor(runner = runner, outputDirectory = outputDirectory)

    private fun inputFile(): File = File(inputDirectory, "clip.mp4").apply { writeText("video") }

    private class WritingRunner(
        private val exitCode: Int = 0,
        private val content: String? = "",
        private val failure: Throwable? = null,
    ) : FfmpegRunner {

        val invocations = mutableListOf<List<String>>()

        override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int {
            invocations += args
            failure?.let { throw it }
            val output = File(args.last())
            content?.let { output.writeText(it) }
            return exitCode
        }
    }

    private companion object {
        const val FINGERPRINT = "AQAAAP//AAAA=="
    }
}
