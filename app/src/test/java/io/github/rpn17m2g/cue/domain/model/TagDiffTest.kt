package io.github.rpn17m2g.cue.domain.model

import io.github.rpn17m2g.cue.tags.TagField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDiffTest {

    private val base = TagDraft(
        title = "Title",
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        year = "2024",
        genre = "Rock",
        trackNumber = 3,
        trackTotal = 10,
    )

    @Test
    fun `an unchanged draft produces no fields`() {
        assertTrue(base.changedFieldsFrom(base).isEmpty())
    }

    @Test
    fun `changed text fields are reported individually`() {
        val edited = base.copy(title = "New title", genre = "Pop")

        assertEquals(
            setOf(TagField.TITLE, TagField.GENRE),
            edited.changedFieldsFrom(base),
        )
    }

    @Test
    fun `clearing a field counts as a change`() {
        assertEquals(
            setOf(TagField.ARTIST),
            base.copy(artist = "").changedFieldsFrom(base),
        )
    }

    @Test
    fun `changing only the track total reports the track field`() {
        assertEquals(
            setOf(TagField.TRACK_NUMBER),
            base.copy(trackTotal = 12).changedFieldsFrom(base),
        )
    }

    @Test
    fun `artwork additions and removals are reported`() {
        val artwork = Artwork(byteArrayOf(1, 2, 3))

        assertEquals(
            setOf(TagField.ARTWORK),
            base.copy(artwork = artwork).changedFieldsFrom(base),
        )
        assertTrue(base.copy(artwork = artwork).changedFieldsFrom(
            base.copy(artwork = Artwork(byteArrayOf(1, 2, 3))),
        ).isEmpty())
        assertEquals(
            setOf(TagField.ARTWORK),
            base.changedFieldsFrom(base.copy(artwork = artwork)),
        )
    }

    @Test
    fun `an equivalent year format is not treated as a change`() {
        assertTrue(base.copy(year = "2024-05-01").changedFieldsFrom(base).isEmpty())
    }

    @Test
    fun `a written tag matches the expected values for the changed fields`() {
        val expected = base.copy(title = "New title", year = "2024-05-01")
        val written = base.copy(title = "New title", year = "2024")

        assertTrue(written.matchesWritten(expected, setOf(TagField.TITLE, TagField.YEAR)))
        assertFalse(
            written.copy(artist = "Another artist")
                .matchesWritten(expected, setOf(TagField.TITLE, TagField.ARTIST)),
        )
        assertFalse(
            written.copy(title = "Something else")
                .matchesWritten(expected, setOf(TagField.TITLE)),
        )
    }

    @Test
    fun `a null track total does not invalidate a match`() {
        val expected = base.copy(trackTotal = null)
        val written = base.copy(trackTotal = 10)

        assertTrue(written.matchesWritten(expected, setOf(TagField.TRACK_NUMBER)))
    }

    @Test
    fun `enrichment fields are reported individually`() {
        val enriched = base.copy(
            lyrics = "la la",
            bpm = 120,
            isrc = "ISRC1",
            label = "Label",
            replayGainDb = -6.3,
            recordingMbid = "rec",
        )

        assertEquals(
            setOf(
                TagField.LYRICS,
                TagField.BPM,
                TagField.ISRC,
                TagField.LABEL,
                TagField.REPLAY_GAIN,
                TagField.MUSICBRAINZ,
            ),
            enriched.changedFieldsFrom(base),
        )
    }

    @Test
    fun `a replay gain difference below the stored precision is not a change`() {
        val base = base.copy(replayGainDb = -6.3)

        assertTrue(base.copy(replayGainDb = -6.32).changedFieldsFrom(base).isEmpty())
        assertEquals(
            setOf(TagField.REPLAY_GAIN),
            base.copy(replayGainDb = -5.0).changedFieldsFrom(base),
        )
    }

    @Test
    fun `a time change in the synced lyrics is a lyrics change`() {
        val timed = base.copy(
            lyrics = "la la",
            syncedLyrics = "[00:01.00]la la\n[00:04.00]la la",
        )
        val retimed = timed.copy(syncedLyrics = "[00:02.00]la la\n[00:04.00]la la")

        assertEquals(setOf(TagField.LYRICS), retimed.changedFieldsFrom(timed))
        assertTrue(
            timed.copy(syncedLyrics = "[00:01.0]la la\n[00:04]la la")
                .changedFieldsFrom(timed)
                .isEmpty(),
        )
    }

    @Test
    fun `a written tag compares the parsed timings`() {
        val expected = base.copy(
            lyrics = "la la",
            syncedLyrics = "[00:01.00]la la\n[00:04.00]la la",
        )
        val written = expected.copy(syncedLyrics = "[00:01.00]la la\n[00:04.50]la la")

        assertTrue(
            expected.copy(syncedLyrics = "[00:01.00]la la\n[00:04.00]la la")
                .matchesWritten(expected, setOf(TagField.LYRICS)),
        )
        assertFalse(written.matchesWritten(expected, setOf(TagField.LYRICS)))
    }

    @Test
    fun `millisecond timings match the centisecond lrc read back from sylt`() {
        // SYLT stores milliseconds, but reading it back goes through the
        // canonical centisecond LRC form, so 1.234 s arrives as 1.23 s.
        val expected = base.copy(
            lyrics = "la la",
            syncedLyrics = "[00:01.23]la la\n[00:04.00]la la",
        )
        val written = expected.copy(syncedLyrics = "[00:01.234]la la\n[00:04.004]la la")

        assertTrue(written.matchesWritten(expected, setOf(TagField.LYRICS)))
        assertTrue(written.changedFieldsFrom(expected).isEmpty())
    }

    @Test
    fun `the three musicbrainz ids count as one changed field`() {
        val base = base.copy(recordingMbid = "rec")

        assertEquals(
            setOf(TagField.MUSICBRAINZ),
            base.copy(releaseMbid = "release").changedFieldsFrom(base),
        )
        assertEquals(
            setOf(TagField.MUSICBRAINZ),
            base.copy(artistMbid = "artist").changedFieldsFrom(base),
        )
    }

    @Test
    fun `a written enrichment tag matches the expected values`() {
        val expected = base.copy(
            lyrics = "la la",
            bpm = 120,
            isrc = "ISRC1",
            label = "Label",
            replayGainDb = -6.3,
            recordingMbid = "rec",
        )
        val written = expected.copy(replayGainDb = -6.3, isrc = " ISRC1 ")

        assertTrue(written.matchesWritten(expected, TagField.TEXT_FIELDS))
        assertFalse(
            written.copy(bpm = 121).matchesWritten(expected, setOf(TagField.BPM)),
        )
        assertFalse(
            written.copy(lyrics = "other").matchesWritten(expected, setOf(TagField.LYRICS)),
        )
        assertFalse(
            written.copy(recordingMbid = "other")
                .matchesWritten(expected, setOf(TagField.MUSICBRAINZ)),
        )
    }
}
