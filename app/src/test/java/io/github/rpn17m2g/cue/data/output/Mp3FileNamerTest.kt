package io.github.rpn17m2g.cue.data.output

import org.junit.Assert.assertEquals
import org.junit.Test

class Mp3FileNamerTest {

    @Test
    fun `an unused name keeps the plain mp3 suffix`() {
        assertEquals(
            "Daft Punk - Get Lucky.mp3",
            Mp3FileNamer.uniqueName("Daft Punk - Get Lucky", emptyList()),
        )
    }

    @Test
    fun `a duplicate gets the next free suffix`() {
        assertEquals(
            "Song (2).mp3",
            Mp3FileNamer.uniqueName("Song", listOf("Song.mp3")),
        )
        assertEquals(
            "Song (3).mp3",
            Mp3FileNamer.uniqueName("Song", listOf("Song.mp3", "Song (2).mp3")),
        )
    }

    @Test
    fun `duplicate detection ignores case`() {
        assertEquals(
            "Song (2).mp3",
            Mp3FileNamer.uniqueName("Song", listOf("song.MP3")),
        )
    }

    @Test
    fun `blank names fall back to unknown track`() {
        assertEquals("Unknown track.mp3", Mp3FileNamer.uniqueName("   ", emptyList()))
    }

    @Test
    fun `folder normalization removes traversal and adds one trailing slash`() {
        assertEquals("Music/Cue/", Mp3FileNamer.normalizeFolder("Music/Cue"))
        assertEquals("Music/Cue/", Mp3FileNamer.normalizeFolder("/Music//Cue/"))
        assertEquals("Cue/", Mp3FileNamer.normalizeFolder("../Cue"))
        assertEquals("Music/Cue/", Mp3FileNamer.normalizeFolder("   "))
    }
}
