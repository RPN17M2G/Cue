package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.ocr.OcrCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationCascadeTest {

    private val getLucky = OcrCandidate(
        title = "Get Lucky",
        artist = "Daft Punk",
        score = 1.0,
    )

    private val cachedMatch = MetadataMatch(
        title = "Get Lucky",
        artist = "Daft Punk",
        album = "Random Access Memories",
        year = "2013",
        source = IdentificationSource.ITUNES,
        score = 0.0,
    )

    @Test
    fun `embedded core tags short circuit the cascade`() = runTest {
        val artwork = Artwork(byteArrayOf(9))
        val embedded = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories",
            year = "2013",
            artwork = artwork,
        )
        val metadata = RecordingMetadataResolver(listOf(cachedMatch))
        val fingerprint = RecordingFingerprintResolver(listOf(cachedMatch))

        val outcome = cascade(metadata, fingerprint).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = embedded,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            rawOcrLines = listOf("raw line"),
        )

        assertEquals(embedded, outcome.tag)
        assertTrue(outcome.usedEmbeddedTags)
        assertFalse(outcome.usedFingerprint)
        assertEquals(listOf("raw line"), outcome.rawOcrLines)
        assertEquals(IdentificationSource.EMBEDDED_TAGS, outcome.sources[TagField.ARTWORK])
        assertEquals(0, metadata.calls)
        assertEquals(0, fingerprint.calls)
    }

    @Test
    fun `ocr seeds the resolver and blanks are filled from the best match`() = runTest {
        val metadata = RecordingMetadataResolver(listOf(cachedMatch))
        val fingerprint = RecordingFingerprintResolver(listOf(cachedMatch))

        val outcome = cascade(metadata, fingerprint).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.ARTIST])
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.ALBUM])
        assertFalse(outcome.usedFingerprint)
        assertFalse(outcome.usedEmbeddedTags)
        assertEquals("Get Lucky", metadata.lastQuery?.title)
        assertEquals("Daft Punk", metadata.lastQuery?.artist)
        assertEquals(0, fingerprint.calls)
    }

    @Test
    fun `resolver alternatives are returned without the chosen match`() = runTest {
        val alternative = cachedMatch.copy(
            title = "Get Lucky (Remix)",
            source = IdentificationSource.MUSICBRAINZ,
        )
        val metadata = RecordingMetadataResolver(listOf(cachedMatch, alternative))

        val outcome = cascade(metadata, RecordingFingerprintResolver()).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(listOf("Get Lucky (Remix)"), outcome.alternatives.map { it.title })
    }

    @Test
    fun `network disabled keeps ocr tags without calling resolvers`() = runTest {
        val metadata = RecordingMetadataResolver(listOf(cachedMatch))
        val fingerprint = RecordingFingerprintResolver(listOf(cachedMatch))

        val outcome = cascade(metadata, fingerprint).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = false,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertEquals(TagDraft(title = "Get Lucky", artist = "Daft Punk"), outcome.tag)
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.TITLE])
        assertTrue(outcome.alternatives.isEmpty())
        assertFalse(outcome.usedFingerprint)
        assertEquals(0, metadata.calls)
        assertEquals(0, fingerprint.calls)
    }

    @Test
    fun `fingerprint fills the gaps when the resolver has no match`() = runTest {
        val fingerprint = RecordingFingerprintResolver(
            listOf(cachedMatch.copy(source = IdentificationSource.FINGERPRINT)),
        )

        val outcome = cascade(RecordingMetadataResolver(), fingerprint).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertTrue(outcome.usedFingerprint)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.FINGERPRINT, outcome.sources[TagField.ALBUM])
        assertEquals(1, fingerprint.calls)
    }

    @Test
    fun `fingerprint is skipped when disabled or unconfigured`() = runTest {
        val fingerprint = RecordingFingerprintResolver(listOf(cachedMatch))
        val cascade = cascade(RecordingMetadataResolver(), fingerprint)

        val disabled = cascade.identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = true,
        )
        val unconfigured = cascade.identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = false,
        )

        assertFalse(disabled.usedFingerprint)
        assertFalse(unconfigured.usedFingerprint)
        assertEquals(0, fingerprint.calls)
    }

    @Test
    fun `manual fallback returns the best available data`() = runTest {
        val outcome = cascade(RecordingMetadataResolver(), RecordingFingerprintResolver()).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
            rawOcrLines = listOf("Get Lucky", "Daft Punk"),
        )

        assertEquals(TagDraft(title = "Get Lucky", artist = "Daft Punk"), outcome.tag)
        assertEquals(listOf("Get Lucky", "Daft Punk"), outcome.rawOcrLines)
        assertFalse(outcome.usedFingerprint)
        assertFalse(outcome.usedEmbeddedTags)
    }

    @Test
    fun `partial embedded tags seed the lookup`() = runTest {
        val embedded = TagDraft(title = "Get Lucky")
        val metadata = RecordingMetadataResolver(listOf(cachedMatch))

        val outcome = cascade(metadata, RecordingFingerprintResolver()).identify(
            ocrCandidates = listOf(OcrCandidate(title = "Wrong", artist = "Wrong Artist", score = 1.0)),
            embeddedTags = embedded,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Wrong Artist", outcome.tag.artist)
        assertEquals(IdentificationSource.EMBEDDED_TAGS, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.ARTIST])
        assertTrue(outcome.usedEmbeddedTags)
        assertEquals("Get Lucky", metadata.lastQuery?.title)
    }

    @Test
    fun `hebrew ocr is tagged as hebrew source`() = runTest {
        val outcome = cascade(RecordingMetadataResolver(), RecordingFingerprintResolver()).identify(
            ocrCandidates = listOf(OcrCandidate(title = "עולם", artist = "שלום", score = 1.0)),
            embeddedTags = null,
            networkEnabled = false,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(IdentificationSource.OCR_HEBREW, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.OCR_HEBREW, outcome.sources[TagField.ARTIST])
    }

    @Test
    fun `empty input still produces a manual fallback`() = runTest {
        val outcome = cascade(RecordingMetadataResolver(), RecordingFingerprintResolver()).identify(
            ocrCandidates = emptyList(),
            embeddedTags = null,
            networkEnabled = false,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(TagDraft(), outcome.tag)
        assertTrue(outcome.sources.isEmpty())
        assertNull(outcome.sources[TagField.TITLE])
    }

    private fun cascade(
        metadata: MetadataResolver,
        fingerprint: FingerprintResolver,
    ): IdentificationCascade = IdentificationCascade(metadata, fingerprint)

    private class RecordingMetadataResolver(
        private val results: List<MetadataMatch> = emptyList(),
    ) : MetadataResolver {
        var calls: Int = 0
        var lastQuery: MetadataQuery? = null

        override suspend fun resolve(query: MetadataQuery): List<MetadataMatch> {
            calls += 1
            lastQuery = query
            return results
        }
    }

    private class RecordingFingerprintResolver(
        private val results: List<MetadataMatch> = emptyList(),
    ) : FingerprintResolver {
        var calls: Int = 0

        override suspend fun identify(): List<MetadataMatch> {
            calls += 1
            return results
        }
    }
}
