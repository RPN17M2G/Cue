package io.github.rpn17m2g.cue.domain.metadata

import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMergerTest {

    @Test
    fun `non blank base values win and keep their source`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punk", year = "2013")
        val match = MetadataMatch(
            title = "Wrong Title",
            artist = "Wrong Artist",
            album = "Random Access Memories",
            year = "1999",
            source = IdentificationSource.ITUNES,
            score = 1.0,
        )

        val result = MetadataMerger.merge(base, listOf(match), baseSource = IdentificationSource.OCR_LATIN)

        assertEquals("Get Lucky", result.tag.title)
        assertEquals("Daft Punk", result.tag.artist)
        assertEquals("2013", result.tag.year)
        assertEquals("Random Access Memories", result.tag.album)
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.TITLE])
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.ARTIST])
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.YEAR])
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ALBUM])
    }

    @Test
    fun `high confidence match overrides ocr fields`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punkk")
        val match = MetadataMatch(
            title = "Get Lucky!",
            artist = "Daft Punk",
            album = "Random Access Memories",
            source = IdentificationSource.ITUNES,
            score = 0.9,
        )

        val result = MetadataMerger.merge(base, listOf(match), baseSource = IdentificationSource.OCR_LATIN)

        assertEquals("Get Lucky!", result.tag.title)
        assertEquals("Daft Punk", result.tag.artist)
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.TITLE])
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ARTIST])
    }

    @Test
    fun `a partial ocr title is replaced by the match that completes it`() {
        val base = TagDraft(title = "I Do if you dont", artist = "Jonas Blue")
        val match = MetadataMatch(
            title = "What Do I Do If You Don't",
            artist = "Jonas Blue",
            source = IdentificationSource.ITUNES,
            score = 0.8,
        )

        val result = MetadataMerger.merge(
            base,
            listOf(match),
            baseSource = IdentificationSource.OCR_LATIN,
        )

        assertEquals("What Do I Do If You Don't", result.tag.title)
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.TITLE])
    }

    @Test
    fun `near exact title overrides a mismatched ocr artist`() {
        val base = TagDraft(title = "Get Lucky", artist = "Wrong Artist")
        val match = MetadataMatch(
            title = "Get Lucky",
            artist = "Daft Punk",
            source = IdentificationSource.ITUNES,
            score = 0.9,
        )

        val result = MetadataMerger.merge(base, listOf(match), baseSource = IdentificationSource.OCR_LATIN)

        assertEquals("Daft Punk", result.tag.artist)
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ARTIST])
    }

    @Test
    fun `high confidence match never overrides manual fields`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punk")
        val match = MetadataMatch(
            title = "Get Lucky!",
            artist = "Daft Punk!",
            source = IdentificationSource.ITUNES,
            score = 1.0,
        )

        val result = MetadataMerger.merge(
            base = base,
            matches = listOf(match),
            baseSource = IdentificationSource.OCR_LATIN,
            baseFieldSources = mapOf(
                TagField.TITLE to IdentificationSource.MANUAL,
                TagField.ARTIST to IdentificationSource.MANUAL,
            ),
        )

        assertEquals("Get Lucky", result.tag.title)
        assertEquals("Daft Punk", result.tag.artist)
        assertEquals(IdentificationSource.MANUAL, result.sources[TagField.TITLE])
        assertEquals(IdentificationSource.MANUAL, result.sources[TagField.ARTIST])
    }

    @Test
    fun `high confidence match overrides ocr fields but not embedded ones`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punk")
        val match = MetadataMatch(
            title = "Get Lucky!",
            artist = "Daft Punk!",
            source = IdentificationSource.ITUNES,
            score = 1.0,
        )

        val result = MetadataMerger.merge(
            base = base,
            matches = listOf(match),
            baseSource = IdentificationSource.OCR_LATIN,
            baseFieldSources = mapOf(TagField.TITLE to IdentificationSource.EMBEDDED_TAGS),
        )

        assertEquals("Get Lucky", result.tag.title)
        assertEquals("Daft Punk!", result.tag.artist)
        assertEquals(IdentificationSource.EMBEDDED_TAGS, result.sources[TagField.TITLE])
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ARTIST])
    }

    @Test
    fun `low confidence match keeps ocr values and still fills blanks`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punk")
        val match = MetadataMatch(
            title = "Completely Different",
            artist = "Nobody Unknown",
            album = "Random Access Memories",
            source = IdentificationSource.ITUNES,
            score = 0.9,
        )

        val result = MetadataMerger.merge(base, listOf(match), baseSource = IdentificationSource.OCR_LATIN)

        assertEquals("Get Lucky", result.tag.title)
        assertEquals("Daft Punk", result.tag.artist)
        assertEquals("Random Access Memories", result.tag.album)
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.TITLE])
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.ARTIST])
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ALBUM])
    }

    @Test
    fun `highest scoring match fills blanks`() {
        val base = TagDraft(title = "Get Lucky")
        val matches = listOf(
            MetadataMatch(
                title = "Get Lucky",
                artist = "Wrong",
                album = "Wrong Album",
                source = IdentificationSource.MUSICBRAINZ,
                score = 0.2,
            ),
            MetadataMatch(
                title = "Get Lucky",
                artist = "Daft Punk",
                album = "Random Access Memories",
                source = IdentificationSource.ITUNES,
                score = 0.9,
            ),
        )

        val result = MetadataMerger.merge(base, matches, baseSource = IdentificationSource.OCR_LATIN)

        assertEquals("Daft Punk", result.tag.artist)
        assertEquals("Random Access Memories", result.tag.album)
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ARTIST])
    }

    @Test
    fun `no matches leave the base untouched`() {
        val base = TagDraft(title = "Get Lucky", artist = "Daft Punk")

        val result = MetadataMerger.merge(base, emptyList(), baseSource = IdentificationSource.OCR_LATIN)

        assertEquals(base, result.tag)
        assertEquals(IdentificationSource.OCR_LATIN, result.sources[TagField.TITLE])
        assertNull(result.sources[TagField.ALBUM])
    }

    @Test
    fun `artwork is filled from the best match`() {
        val artwork = Artwork(byteArrayOf(1, 2, 3))
        val match = MetadataMatch(
            title = "Get Lucky",
            artist = "Daft Punk",
            artwork = artwork,
            source = IdentificationSource.ITUNES,
            score = 1.0,
        )

        val result = MetadataMerger.merge(TagDraft(), listOf(match))

        assertSame(artwork, result.tag.artwork)
        assertEquals(IdentificationSource.ITUNES, result.sources[TagField.ARTWORK])
    }

    @Test
    fun `base field source overrides the default base source`() {
        val base = TagDraft(title = "Get Lucky")

        val result = MetadataMerger.merge(
            base = base,
            matches = emptyList(),
            baseSource = IdentificationSource.OCR_LATIN,
            baseFieldSources = mapOf(TagField.TITLE to IdentificationSource.EMBEDDED_TAGS),
        )

        assertEquals(IdentificationSource.EMBEDDED_TAGS, result.sources[TagField.TITLE])
    }

    @Test
    fun `nullable and numeric fields are filled when blank`() {
        val match = MetadataMatch(
            title = "Get Lucky",
            artist = "Daft Punk",
            albumArtist = "Daft Punk",
            year = "2013",
            genre = "Disco",
            trackNumber = 8,
            source = IdentificationSource.ITUNES,
            score = 1.0,
        )

        val result = MetadataMerger.merge(TagDraft(), listOf(match))

        assertEquals("Daft Punk", result.tag.albumArtist)
        assertEquals("2013", result.tag.year)
        assertEquals("Disco", result.tag.genre)
        assertEquals(8, result.tag.trackNumber)
        assertTrue(result.sources.containsKey(TagField.TRACK_NUMBER))
    }
}
