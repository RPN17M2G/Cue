package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.ocr.OcrCandidate
import kotlinx.coroutines.CancellationException
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
        var lookupCalls = 0
        var fingerprintCalls = 0

        val outcome = cascade(
            lookup = {
                lookupCalls += 1
                listOf(cachedMatch)
            },
            fingerprintLookup = {
                fingerprintCalls += 1
                FingerprintAttempt(listOf(cachedMatch))
            },
        ).identify(
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
        assertEquals(0, lookupCalls)
        assertEquals(0, fingerprintCalls)
    }

    @Test
    fun `ocr seeds the lookup and the high confidence match fills the gaps`() = runTest {
        var lastSeed: LookupSeed? = null
        var fingerprintCalls = 0

        val outcome = cascade(
            lookup = { seed ->
                lastSeed = seed
                listOf(cachedMatch)
            },
            fingerprintLookup = {
                fingerprintCalls += 1
                FingerprintAttempt(listOf(cachedMatch))
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            durationMs = 12_345,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.ARTIST])
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.ALBUM])
        assertFalse(outcome.usedFingerprint)
        assertFalse(outcome.usedEmbeddedTags)
        assertEquals("Get Lucky", lastSeed?.title)
        assertEquals("Daft Punk", lastSeed?.artist)
        assertEquals(12_345L, lastSeed?.durationMs)
        assertEquals(0, fingerprintCalls)
    }

    @Test
    fun `the highest scoring ocr candidate seeds the lookup first`() = runTest {
        val seeds = mutableListOf<LookupSeed>()
        val candidates = listOf(
            OcrCandidate(title = "Wrong Title", artist = "Wrong Artist", score = 0.2),
            OcrCandidate(title = "Get Lucky", artist = "Daft Punk", score = 0.9),
        )

        val outcome = cascade(lookup = { seed -> seeds += seed; emptyList() }).identify(
            ocrCandidates = candidates,
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals("Get Lucky", seeds.first().title)
        assertEquals("Daft Punk", seeds.first().artist)
        assertEquals("Get Lucky", outcome.tag.title)
    }

    @Test
    fun `a later ocr candidate answers when the top one does not`() = runTest {
        val candidates = listOf(
            OcrCandidate(title = "Wrong Title", artist = null, score = 0.9),
            OcrCandidate(title = "Get Lucky", artist = "Daft Punk", score = 0.6),
        )
        val seeds = mutableListOf<String>()

        val outcome = cascade(
            lookup = { seed ->
                seeds += seed.title
                if (seed.title == "Get Lucky") listOf(cachedMatch) else emptyList()
            },
        ).identify(
            ocrCandidates = candidates,
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(listOf("Wrong Title", "Get Lucky"), seeds)
        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.TITLE])
    }

    @Test
    fun `resolver alternatives are returned without the chosen match`() = runTest {
        val alternative = cachedMatch.copy(
            title = "Get Lucky (Remix)",
            source = IdentificationSource.MUSICBRAINZ,
        )

        val outcome = cascade(lookup = { listOf(cachedMatch, alternative) }).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(listOf("Get Lucky (Remix)"), outcome.alternatives.map { it.title })
    }

    @Test
    fun `network disabled keeps ocr tags without calling lookups`() = runTest {
        var lookupCalls = 0
        var fingerprintCalls = 0

        val outcome = cascade(
            lookup = {
                lookupCalls += 1
                listOf(cachedMatch)
            },
            fingerprintLookup = {
                fingerprintCalls += 1
                FingerprintAttempt(listOf(cachedMatch))
            },
        ).identify(
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
        assertEquals(0, lookupCalls)
        assertEquals(0, fingerprintCalls)
    }

    @Test
    fun `fingerprint with no readable title picks the provider top match`() = runTest {
        val lowScore = MetadataMatch(
            title = "Alpha",
            artist = "Low",
            album = "Low Album",
            source = IdentificationSource.FINGERPRINT,
            score = 0.2,
        )
        val highScore = MetadataMatch(
            title = "Zulu",
            artist = "High",
            album = "High Album",
            source = IdentificationSource.FINGERPRINT,
            score = 0.9,
        )

        val outcome = cascade(
            fingerprintLookup = { FingerprintAttempt(listOf(lowScore, highScore)) },
        ).identify(
            ocrCandidates = emptyList(),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertEquals("Zulu", outcome.tag.title)
        assertEquals("High Album", outcome.tag.album)
        // Below the ignore threshold the candidate is noise, not an alternative.
        assertTrue(outcome.alternatives.isEmpty())
    }

    @Test
    fun `a weak lookup match is ignored and the fingerprint still runs`() = runTest {
        val noise = MetadataMatch(
            title = "Completely Different Song",
            artist = "Somebody Else",
            album = "Noise",
            source = IdentificationSource.ITUNES,
            score = 0.0,
        )
        var fingerprintCalls = 0

        val outcome = cascade(
            lookup = { listOf(noise) },
            fingerprintLookup = {
                fingerprintCalls += 1
                FingerprintAttempt(
                    listOf(cachedMatch.copy(source = IdentificationSource.FINGERPRINT)),
                )
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertTrue(outcome.usedFingerprint)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.FINGERPRINT, outcome.sources[TagField.ALBUM])
        assertEquals(1, fingerprintCalls)
    }

    @Test
    fun `weak matches never populate the draft`() = runTest {
        val noise = MetadataMatch(
            title = "Completely Different Song",
            artist = "Somebody Else",
            album = "Noise",
            source = IdentificationSource.ITUNES,
            score = 0.0,
        )

        val outcome = cascade(lookup = { listOf(noise) }).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(TagDraft(title = "Get Lucky", artist = "Daft Punk"), outcome.tag)
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.TITLE])
        assertNull(outcome.bestMatch)
        assertTrue(outcome.alternatives.isEmpty())
    }

    @Test
    fun `fingerprint fills the gaps when the resolver has no match`() = runTest {
        var fingerprintSeed: FingerprintSeed? = null

        val outcome = cascade(
            fingerprintLookup = { seed ->
                fingerprintSeed = seed
                FingerprintAttempt(listOf(cachedMatch.copy(source = IdentificationSource.FINGERPRINT)))
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            durationMs = 45_000,
        )

        assertTrue(outcome.usedFingerprint)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.FINGERPRINT, outcome.sources[TagField.ALBUM])
        assertEquals("Get Lucky", fingerprintSeed?.title)
        assertEquals("Daft Punk", fingerprintSeed?.artist)
        assertEquals(45_000L, fingerprintSeed?.durationMs)
    }

    @Test
    fun `fingerprint is told the whole track length read from the player`() = runTest {
        var fingerprintSeed: FingerprintSeed? = null

        cascade(
            fingerprintLookup = { seed ->
                fingerprintSeed = seed
                FingerprintAttempt(listOf(cachedMatch.copy(source = IdentificationSource.FINGERPRINT)))
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            durationMs = 30_000,
            trackDurationMs = 204_000,
        )

        assertEquals(204_000L, fingerprintSeed?.durationMs)
    }

    @Test
    fun `fingerprint is skipped when disabled or unconfigured`() = runTest {
        var fingerprintCalls = 0
        val cascade = cascade(
            fingerprintLookup = {
                fingerprintCalls += 1
                FingerprintAttempt(listOf(cachedMatch))
            },
        )

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
        assertEquals(0, fingerprintCalls)
    }

    @Test
    fun `cancellation from the lookup propagates`() = runTest {
        var caught: Throwable? = null

        try {
            cascade(lookup = { throw CancellationException("cancelled") }).identify(
                ocrCandidates = listOf(getLucky),
                embeddedTags = null,
                networkEnabled = true,
                fingerprintEnabled = false,
                acoustIdConfigured = false,
            )
        } catch (error: CancellationException) {
            caught = error
        }

        assertTrue(caught is CancellationException)
    }

    @Test
    fun `resolver failure falls through to the fingerprint`() = runTest {
        val outcome = cascade(
            lookup = { throw IllegalStateException("network down") },
            fingerprintLookup = { FingerprintAttempt(listOf(cachedMatch.copy(source = IdentificationSource.FINGERPRINT))) },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertTrue(outcome.usedFingerprint)
        assertEquals("Random Access Memories", outcome.tag.album)
    }

    @Test
    fun `resolver failure returns the manual fallback`() = runTest {
        val outcome = cascade(
            lookup = { throw IllegalStateException("network down") },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertEquals(IdentificationSource.OCR_LATIN, outcome.sources[TagField.TITLE])
        assertFalse(outcome.usedFingerprint)
    }

    @Test
    fun `a rejected fingerprint key is reported instead of looking like an unknown song`() = runTest {
        val outcome = cascade(
            fingerprintLookup = { FingerprintAttempt(keyRejected = true) },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertTrue(outcome.fingerprintKeyRejected)
        assertFalse(outcome.usedFingerprint)
        assertEquals("Get Lucky", outcome.tag.title)
    }

    @Test
    fun `an accepted key with no match is not reported as rejected`() = runTest {
        val outcome = cascade(
            fingerprintLookup = { FingerprintAttempt(emptyList()) },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertFalse(outcome.fingerprintKeyRejected)
    }

    @Test
    fun `manual fingerprint runs before the text lookup`() = runTest {
        val calls = mutableListOf<String>()

        val outcome = cascade(
            lookup = {
                calls += "text"
                listOf(cachedMatch)
            },
            fingerprintLookup = {
                calls += "fingerprint"
                FingerprintAttempt(
                    listOf(
                        cachedMatch.copy(
                            score = 0.9,
                            source = IdentificationSource.FINGERPRINT,
                        ),
                    ),
                )
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            forceFingerprint = true,
        )

        assertEquals(listOf("fingerprint"), calls)
        assertTrue(outcome.usedFingerprint)
        assertTrue(outcome.fingerprintTried)
        assertEquals(IdentificationSource.FINGERPRINT, outcome.sources[TagField.ALBUM])
    }

    @Test
    fun `manual fingerprint falls back to the text lookup when it has no match`() = runTest {
        val calls = mutableListOf<String>()

        val outcome = cascade(
            lookup = {
                calls += "text"
                listOf(cachedMatch)
            },
            fingerprintLookup = {
                calls += "fingerprint"
                FingerprintAttempt(emptyList())
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            forceFingerprint = true,
        )

        assertEquals(listOf("fingerprint", "text"), calls)
        assertFalse(outcome.usedFingerprint)
        assertTrue(outcome.fingerprintTried)
        assertEquals("Random Access Memories", outcome.tag.album)
    }

    @Test
    fun `manual fingerprint is not scored against a wrong OCR title`() = runTest {
        val outcome = cascade(
            lookup = { emptyList() },
            fingerprintLookup = {
                FingerprintAttempt(
                    listOf(
                        cachedMatch.copy(
                            score = 0.9,
                            source = IdentificationSource.FINGERPRINT,
                        ),
                    ),
                )
            },
        ).identify(
            ocrCandidates = listOf(
                OcrCandidate(title = "Totally Wrong", artist = "Nobody", score = 1.0),
            ),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
            forceFingerprint = true,
        )

        assertTrue(outcome.usedFingerprint)
        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Random Access Memories", outcome.tag.album)
    }

    @Test
    fun `fingerprint failure returns the manual fallback`() = runTest {
        val outcome = cascade(
            fingerprintLookup = { throw IllegalStateException("fingerprint service down") },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = true,
            acoustIdConfigured = true,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertFalse(outcome.usedFingerprint)
    }

    @Test
    fun `manual fallback returns the best available data`() = runTest {
        val outcome = cascade().identify(
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
        var lastSeed: LookupSeed? = null

        val outcome = cascade(
            lookup = { seed ->
                lastSeed = seed
                listOf(cachedMatch)
            },
        ).identify(
            ocrCandidates = listOf(OcrCandidate(title = "Wrong", artist = "Wrong Artist", score = 1.0)),
            embeddedTags = embedded,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertEquals(IdentificationSource.EMBEDDED_TAGS, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.ARTIST])
        assertTrue(outcome.usedEmbeddedTags)
        assertEquals("Get Lucky", lastSeed?.title)
    }

    @Test
    fun `lookup seed forwards the album from embedded tags`() = runTest {
        var lastSeed: LookupSeed? = null
        val embedded = TagDraft(album = "Random Access Memories")

        cascade(
            lookup = { seed ->
                lastSeed = seed
                emptyList()
            },
        ).identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = embedded,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals("Random Access Memories", lastSeed?.album)
    }

    @Test
    fun `hebrew ocr is tagged as hebrew source`() = runTest {
        val outcome = cascade().identify(
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
    fun `empty input still produces a manual fallback and skips an empty lookup`() = runTest {
        var lookupCalls = 0

        val outcome = cascade(
            lookup = {
                lookupCalls += 1
                listOf(cachedMatch)
            },
        ).identify(
            ocrCandidates = emptyList(),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
        )

        assertEquals(TagDraft(), outcome.tag)
        assertTrue(outcome.sources.isEmpty())
        assertNull(outcome.sources[TagField.TITLE])
        assertEquals(0, lookupCalls)
    }

    @Test
    fun `manual query searches with the corrected text instead of the ocr reading`() = runTest {
        val seeds = mutableListOf<LookupSeed>()
        val wrongOcr = OcrCandidate(title = "Wrong Title", artist = "Wrong Artist", score = 1.0)

        val outcome = cascade(
            lookup = { seed ->
                seeds += seed
                listOf(cachedMatch)
            },
        ).identify(
            ocrCandidates = listOf(wrongOcr),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
            durationMs = 12_345,
            manualQuery = LookupSeed(
                title = "Get Lucky",
                artist = "Daft Punk",
                durationMs = 12_345,
            ),
        )

        assertEquals(1, seeds.size)
        assertEquals("Get Lucky", seeds.single().title)
        assertEquals("Daft Punk", seeds.single().artist)
        assertEquals(12_345L, seeds.single().durationMs)
        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Random Access Memories", outcome.tag.album)
        assertEquals(IdentificationSource.ITUNES, outcome.sources[TagField.ALBUM])
    }

    @Test
    fun `manual query is not short circuited by embedded core tags`() = runTest {
        var lookupCalls = 0
        val embedded = TagDraft(
            title = "Old Tag",
            artist = "Old Artist",
            album = "Old Album",
        )

        val outcome = cascade(
            lookup = {
                lookupCalls += 1
                listOf(cachedMatch)
            },
        ).identify(
            ocrCandidates = emptyList(),
            embeddedTags = embedded,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
            manualQuery = LookupSeed(title = "Get Lucky", artist = "Daft Punk"),
        )

        assertEquals(1, lookupCalls)
        assertEquals("Get Lucky", outcome.tag.title)
        assertEquals("Daft Punk", outcome.tag.artist)
        assertFalse(outcome.usedEmbeddedTags)
    }

    @Test
    fun `manual query keeps the corrected text when the lookup finds nothing`() = runTest {
        val outcome = cascade().identify(
            ocrCandidates = listOf(getLucky),
            embeddedTags = null,
            networkEnabled = true,
            fingerprintEnabled = false,
            acoustIdConfigured = false,
            manualQuery = LookupSeed(
                title = "My Title",
                artist = "My Artist",
                album = "My Album",
            ),
        )

        assertEquals("My Title", outcome.tag.title)
        assertEquals("My Artist", outcome.tag.artist)
        assertEquals("My Album", outcome.tag.album)
        assertEquals(IdentificationSource.MANUAL, outcome.sources[TagField.TITLE])
        assertEquals(IdentificationSource.MANUAL, outcome.sources[TagField.ARTIST])
        assertFalse(outcome.usedEmbeddedTags)
    }

    private fun cascade(
        lookup: suspend (LookupSeed) -> List<MetadataMatch> = { emptyList() },
        fingerprintLookup: suspend (FingerprintSeed) -> FingerprintAttempt = { FingerprintAttempt() },
    ): IdentificationCascade = IdentificationCascade(lookup, fingerprintLookup)
}
