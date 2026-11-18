package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.TagDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataEnricherTest {

    private val draft = TagDraft(title = "By Your Side", artist = "Jonas Blue", album = "Blue")

    @Test
    fun `fills lyrics bpm gain and isrc on a blank draft`() = runTest {
        val result = enricher().enrich(draft, durationSeconds = 204)

        assertEquals("I want to be by your side", result.tag.lyrics)
        assertEquals("[00:01.00]I want to be by your side", result.tag.syncedLyrics)
        assertEquals(124, result.tag.bpm)
        assertEquals(-6.3, result.tag.replayGainDb!!, 0.0001)
        assertEquals("GBUM71700001", result.tag.isrc)
        assertEquals(IdentificationSource.LRCLIB, result.sources[TagField.LYRICS])
        assertEquals(IdentificationSource.DEEZER, result.sources[TagField.BPM])
        assertEquals(IdentificationSource.DEEZER, result.sources[TagField.REPLAY_GAIN])
        assertEquals(IdentificationSource.DEEZER, result.sources[TagField.ISRC])
    }

    @Test
    fun `never overwrites a value the draft already has`() = runTest {
        val existing = draft.copy(
            lyrics = "My own words",
            bpm = 100,
            replayGainDb = -1.0,
            isrc = "EXISTING",
        )

        val result = enricher().enrich(existing, durationSeconds = 204)

        assertEquals("My own words", result.tag.lyrics)
        assertEquals(100, result.tag.bpm)
        assertEquals(-1.0, result.tag.replayGainDb!!, 0.0001)
        assertEquals("EXISTING", result.tag.isrc)
        assertTrue(result.sources.isEmpty())
    }

    @Test
    fun `fills only the blanks when some fields are already set`() = runTest {
        val partial = draft.copy(lyrics = "Already here", bpm = 90)

        val result = enricher().enrich(partial, durationSeconds = 204)

        assertEquals("Already here", result.tag.lyrics)
        assertEquals(90, result.tag.bpm)
        assertEquals(-6.3, result.tag.replayGainDb!!, 0.0001)
        assertEquals("GBUM71700001", result.tag.isrc)
        assertNull(result.sources[TagField.LYRICS])
        assertNull(result.sources[TagField.BPM])
        assertEquals(IdentificationSource.DEEZER, result.sources[TagField.REPLAY_GAIN])
    }

    @Test
    fun `a lyrics failure does not block the deezer fields`() = runTest {
        val result = enricher(
            lyrics = { _, _, _, _ -> error("lyrics exploded") },
        ).enrich(draft, durationSeconds = 204)

        assertNull(result.tag.lyrics)
        assertEquals(124, result.tag.bpm)
        assertEquals("GBUM71700001", result.tag.isrc)
        assertNull(result.sources[TagField.LYRICS])
        assertEquals(IdentificationSource.DEEZER, result.sources[TagField.BPM])
    }

    @Test
    fun `a deezer failure does not block the lyrics`() = runTest {
        val result = enricher(
            bpm = { _, _ -> error("deezer exploded") },
        ).enrich(draft, durationSeconds = 204)

        assertEquals("I want to be by your side", result.tag.lyrics)
        assertNull(result.tag.bpm)
        assertNull(result.tag.isrc)
        assertEquals(IdentificationSource.LRCLIB, result.sources[TagField.LYRICS])
    }

    @Test
    fun `nothing to add leaves the draft untouched`() = runTest {
        var lyricsCalls = 0
        var bpmCalls = 0
        val full = draft.copy(
            lyrics = "words",
            bpm = 100,
            replayGainDb = -1.0,
            isrc = "EXISTING",
        )

        val result = enricher(
            lyrics = { _, _, _, _ ->
                lyricsCalls++
                null
            },
            bpm = { _, _ ->
                bpmCalls++
                null
            },
        ).enrich(full, durationSeconds = 204)

        assertEquals(full, result.tag)
        assertTrue(result.sources.isEmpty())
        assertEquals(0, lyricsCalls)
        assertEquals(0, bpmCalls)
    }

    @Test
    fun `a blank title or artist skips every lookup`() = runTest {
        var calls = 0

        val result = enricher(
            lyrics = { _, _, _, _ ->
                calls++
                null
            },
            bpm = { _, _ ->
                calls++
                null
            },
        ).enrich(TagDraft(title = "", artist = "Jonas Blue"), durationSeconds = 204)

        assertEquals(0, calls)
        assertTrue(result.sources.isEmpty())
    }

    @Test
    fun `a closed network gate skips every lookup`() = runTest {
        var calls = 0

        val result = enricher(
            lyrics = { _, _, _, _ ->
                calls++
                null
            },
            bpm = { _, _ ->
                calls++
                null
            },
            networkGate = NetworkGate.Disabled,
        ).enrich(draft, durationSeconds = 204)

        assertEquals(0, calls)
        assertTrue(result.sources.isEmpty())
    }

    @Test
    fun `passes the title artist album and duration to the lyrics lookup`() = runTest {
        var received: List<Any?> = emptyList()

        enricher(
            lyrics = { title, artist, album, duration ->
                received = listOf(title, artist, album, duration)
                null
            },
        ).enrich(draft, durationSeconds = 204)

        assertEquals(listOf("By Your Side", "Jonas Blue", "Blue", 204), received)
    }

    @Test
    fun `a blank album is passed as null and a non-positive duration is dropped`() = runTest {
        var received: List<Any?> = emptyList()

        enricher(
            lyrics = { title, artist, album, duration ->
                received = listOf(title, artist, album, duration)
                null
            },
        ).enrich(draft.copy(album = "   "), durationSeconds = 0)

        assertEquals(listOf("By Your Side", "Jonas Blue", null, null), received)
    }

    private fun enricher(
        lyrics: LyricsSource = LyricsSource { _, _, _, _ ->
            Lyrics("I want to be by your side", "[00:01.00]I want to be by your side")
        },
        bpm: BpmSource = BpmSource { _, _ ->
            DeezerTrack(
                title = "By Your Side",
                artist = "Jonas Blue",
                bpm = 124,
                replayGainDb = -6.3,
                isrc = "GBUM71700001",
            )
        },
        networkGate: NetworkGate = NetworkGate.Enabled,
    ): MetadataEnricher = MetadataEnricher(lyrics, bpm, networkGate)
}
