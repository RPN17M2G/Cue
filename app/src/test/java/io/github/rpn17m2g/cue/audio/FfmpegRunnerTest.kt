package io.github.rpn17m2g.cue.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FfmpegRunnerTest {

    @Test
    fun `fake runner returns its exit code and forwards lines in order`() = runBlocking {
        val runner = FakeFfmpegRunner(exitCode = 3, lines = listOf("first", "second"))
        val received = mutableListOf<String>()

        val exit = runner.run(listOf("-i", "song.mp4")) { received += it }

        assertEquals(3, exit)
        assertEquals(listOf("first", "second"), received)
        assertEquals(listOf(listOf("-i", "song.mp4")), runner.invocations)
    }
}
