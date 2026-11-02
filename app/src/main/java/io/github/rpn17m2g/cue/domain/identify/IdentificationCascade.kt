package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.domain.metadata.MetadataMatcher
import io.github.rpn17m2g.cue.domain.metadata.MetadataMerger
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.ocr.OcrCandidate
import kotlinx.coroutines.CancellationException

data class LookupSeed(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

data class FingerprintSeed(
    val title: String,
    val artist: String? = null,
    val durationMs: Long? = null,
)

/**
 * What the fingerprint stage produced, and why it produced nothing when it did
 * not. An empty [matches] with [keyRejected] means the provider refused the
 * configured key, which is a fixable setup problem rather than an unknown song.
 */
data class FingerprintAttempt(
    val matches: List<MetadataMatch> = emptyList(),
    val keyRejected: Boolean = false,
)

data class IdentificationOutcome(
    val tag: TagDraft,
    val sources: Map<TagField, IdentificationSource>,
    val alternatives: List<MetadataMatch>,
    val rawOcrLines: List<String>,
    val usedFingerprint: Boolean,
    val usedEmbeddedTags: Boolean,
    val fingerprintKeyRejected: Boolean = false,
    val bestMatch: MetadataMatch? = null,
)

/**
 * Pure orchestration of the identification stages. Embedded core tags stop the
 * cascade; otherwise OCR seeds the online lookup, then the fingerprint, and the
 * best available draft is always returned for manual correction.
 *
 * Resolver failures are treated as empty results so the cascade degrades to the
 * next stage instead of aborting the flow. Cancellation always propagates.
 */
class IdentificationCascade(
    private val lookup: suspend (LookupSeed) -> List<MetadataMatch>,
    private val fingerprintLookup: suspend (FingerprintSeed) -> FingerprintAttempt,
) {

    suspend fun identify(
        ocrCandidates: List<OcrCandidate>,
        embeddedTags: TagDraft?,
        networkEnabled: Boolean,
        fingerprintEnabled: Boolean,
        acoustIdConfigured: Boolean,
        rawOcrLines: List<String> = emptyList(),
        durationMs: Long? = null,
    ): IdentificationOutcome {
        if (embeddedTags != null && embeddedTags.hasCoreTags) {
            return IdentificationOutcome(
                tag = embeddedTags,
                sources = sourcesOf(embeddedTags),
                alternatives = emptyList(),
                rawOcrLines = rawOcrLines,
                usedFingerprint = false,
                usedEmbeddedTags = true,
            )
        }

        val seed = seed(embeddedTags, ocrCandidates.maxByOrNull { it.score })

        if (networkEnabled && seed.tag.title.isNotBlank()) {
            val lookupSeed = LookupSeed(
                title = seed.tag.title,
                artist = seed.tag.artist.takeIf { it.isNotBlank() },
                album = seed.tag.album.takeIf { it.isNotBlank() },
                durationMs = durationMs,
            )
            val matches = lookupSafely { lookup(lookupSeed) }
            val ranked = MetadataMatcher.rank(seed.tag, matches).accepted()
            if (ranked.isNotEmpty()) {
                return outcome(seed, ranked, rawOcrLines, usedFingerprint = false)
            }
        }

        if (networkEnabled && fingerprintEnabled && acoustIdConfigured) {
            val fingerprintSeed = FingerprintSeed(
                title = seed.tag.title,
                artist = seed.tag.artist.takeIf { it.isNotBlank() },
                durationMs = durationMs,
            )
            val attempt = fingerprintSafely { fingerprintLookup(fingerprintSeed) }
            val ranked = MetadataMatcher.rank(seed.tag, attempt.matches).accepted()
            if (ranked.isNotEmpty()) {
                return outcome(seed, ranked, rawOcrLines, usedFingerprint = true)
            }
            if (attempt.keyRejected) {
                return IdentificationOutcome(
                    tag = seed.tag,
                    sources = seed.sources,
                    alternatives = emptyList(),
                    rawOcrLines = rawOcrLines,
                    usedFingerprint = false,
                    usedEmbeddedTags = seed.sources.containsValue(IdentificationSource.EMBEDDED_TAGS),
                    fingerprintKeyRejected = true,
                )
            }
        }

        return IdentificationOutcome(
            tag = seed.tag,
            sources = seed.sources,
            alternatives = emptyList(),
            rawOcrLines = rawOcrLines,
            usedFingerprint = false,
            usedEmbeddedTags = seed.sources.containsValue(IdentificationSource.EMBEDDED_TAGS),
        )
    }

    private suspend fun lookupSafely(block: suspend () -> List<MetadataMatch>): List<MetadataMatch> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

    private suspend fun fingerprintSafely(
        block: suspend () -> FingerprintAttempt,
    ): FingerprintAttempt =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FingerprintAttempt()
        }

    /**
     * Below the ignore threshold a match is noise: it would populate fields the
     * user has to undo, and it must not stop the cascade from trying the next
     * stage. The thresholds live in [Confidence] so the UI and this filter
     * cannot drift apart.
     */
    private fun List<MetadataMatch>.accepted(): List<MetadataMatch> =
        filter { Confidence.tier(it.score) != ConfidenceTier.IGNORED }

    private fun outcome(
        seed: Seed,
        ranked: List<MetadataMatch>,
        rawOcrLines: List<String>,
        usedFingerprint: Boolean,
    ): IdentificationOutcome {        val merged = MetadataMerger.merge(
            base = seed.tag,
            matches = ranked,
            baseSource = seed.defaultSource,
            baseFieldSources = seed.sources,
        )
        return IdentificationOutcome(
            tag = merged.tag,
            sources = merged.sources,
            alternatives = ranked.drop(1),
            rawOcrLines = rawOcrLines,
            usedFingerprint = usedFingerprint,
            usedEmbeddedTags = merged.sources.containsValue(IdentificationSource.EMBEDDED_TAGS),
            bestMatch = ranked.firstOrNull(),
        )
    }

    private fun seed(embedded: TagDraft?, ocr: OcrCandidate?): Seed {
        val sources = linkedMapOf<TagField, IdentificationSource>()
        val ocrSource = if (containsHebrew(ocr?.title) || containsHebrew(ocr?.artist)) {
            IdentificationSource.OCR_HEBREW
        } else {
            IdentificationSource.OCR_LATIN
        }

        fun text(field: TagField, embeddedValue: String, ocrValue: String?): String {
            if (embeddedValue.isNotBlank()) {
                sources[field] = IdentificationSource.EMBEDDED_TAGS
                return embeddedValue
            }
            if (!ocrValue.isNullOrBlank()) {
                sources[field] = ocrSource
                return ocrValue
            }
            return ""
        }

        val title = text(TagField.TITLE, embedded?.title.orEmpty(), ocr?.title)
        val artist = text(TagField.ARTIST, embedded?.artist.orEmpty(), ocr?.artist)
        val album = text(TagField.ALBUM, embedded?.album.orEmpty(), null)
        val albumArtist = text(TagField.ALBUM_ARTIST, embedded?.albumArtist.orEmpty(), null)

        val embeddedYear = embedded?.year
        if (!embeddedYear.isNullOrBlank()) sources[TagField.YEAR] = IdentificationSource.EMBEDDED_TAGS
        val embeddedGenre = embedded?.genre
        if (!embeddedGenre.isNullOrBlank()) sources[TagField.GENRE] = IdentificationSource.EMBEDDED_TAGS
        val embeddedTrack = embedded?.trackNumber
        if (embeddedTrack != null) sources[TagField.TRACK_NUMBER] = IdentificationSource.EMBEDDED_TAGS
        val embeddedArtwork = embedded?.artwork
        if (embeddedArtwork != null) sources[TagField.ARTWORK] = IdentificationSource.EMBEDDED_TAGS

        return Seed(
            tag = TagDraft(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                year = embeddedYear,
                genre = embeddedGenre,
                trackNumber = embeddedTrack,
                artwork = embeddedArtwork,
            ),
            sources = sources,
            defaultSource = ocrSource,
        )
    }

    private fun sourcesOf(tag: TagDraft): Map<TagField, IdentificationSource> {
        val sources = linkedMapOf<TagField, IdentificationSource>()
        if (tag.title.isNotBlank()) sources[TagField.TITLE] = IdentificationSource.EMBEDDED_TAGS
        if (tag.artist.isNotBlank()) sources[TagField.ARTIST] = IdentificationSource.EMBEDDED_TAGS
        if (tag.album.isNotBlank()) sources[TagField.ALBUM] = IdentificationSource.EMBEDDED_TAGS
        if (tag.albumArtist.isNotBlank()) sources[TagField.ALBUM_ARTIST] = IdentificationSource.EMBEDDED_TAGS
        if (!tag.year.isNullOrBlank()) sources[TagField.YEAR] = IdentificationSource.EMBEDDED_TAGS
        if (!tag.genre.isNullOrBlank()) sources[TagField.GENRE] = IdentificationSource.EMBEDDED_TAGS
        if (tag.trackNumber != null) sources[TagField.TRACK_NUMBER] = IdentificationSource.EMBEDDED_TAGS
        if (tag.artwork != null) sources[TagField.ARTWORK] = IdentificationSource.EMBEDDED_TAGS
        return sources
    }

    private data class Seed(
        val tag: TagDraft,
        val sources: Map<TagField, IdentificationSource>,
        val defaultSource: IdentificationSource,
    )

    private fun containsHebrew(value: String?): Boolean =
        value != null && HEBREW.containsMatchIn(value)

    private companion object {
        val HEBREW = Regex("[\\u0590-\\u05FF\\uFB1D-\\uFB4F]")
    }
}
