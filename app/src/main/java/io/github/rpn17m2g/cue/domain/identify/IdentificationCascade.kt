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

/** The same shape for ACRCloud, which recognizes a fragment from anywhere. */
data class AudioRecognitionAttempt(
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
    val fingerprintTried: Boolean = false,
    val usedAcrCloud: Boolean = false,
    val acrCloudKeyRejected: Boolean = false,
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
    /**
     * ACRCloud, or another recognizer that accepts a fragment from anywhere in
     * the song. Null when the feature is not wired; the cascade then behaves
     * exactly as before. The argument is the known whole-track length.
     */
    private val audioLookup: (suspend (Long?) -> AudioRecognitionAttempt)? = null,
) {

    suspend fun identify(
        ocrCandidates: List<OcrCandidate>,
        embeddedTags: TagDraft?,
        networkEnabled: Boolean,
        fingerprintEnabled: Boolean,
        acoustIdConfigured: Boolean,
        acrCloudConfigured: Boolean = false,
        rawOcrLines: List<String> = emptyList(),
        durationMs: Long? = null,
        /**
         * Length of the whole song when the player UI showed it. AcoustID is a
         * full-file service: it filters candidates by the duration of the
         * recording, so a 30 second excerpt of a 3:24 track must be reported as
         * 204 seconds or the real recording is excluded before scoring.
         */
        trackDurationMs: Long? = null,
        /**
         * Manual "use fingerprint" asks the audio stage to run even when the
         * text lookup would normally answer first. The text lookup still runs
         * as a fallback, so a forced fingerprint that finds nothing cannot
         * leave the user worse off than before.
         */
        forceFingerprint: Boolean = false,
        /**
         * The user corrected the title or artist and asked to search again.
         * These values replace the OCR reading as the lookup seed, and
         * embedded tags no longer short-circuit the run: the user explicitly
         * asked for a fresh online search with what is on screen.
         */
        manualQuery: LookupSeed? = null,
    ): IdentificationOutcome {
        if (manualQuery == null && embeddedTags != null && embeddedTags.hasCoreTags) {
            return IdentificationOutcome(
                tag = embeddedTags,
                sources = sourcesOf(embeddedTags),
                alternatives = emptyList(),
                rawOcrLines = rawOcrLines,
                usedFingerprint = false,
                usedEmbeddedTags = true,
            )
        }

        val manualSeed = manualQuery?.takeIf { it.title.isNotBlank() }?.let(::manualSeed)
        val seed = manualSeed ?: seed(embeddedTags, ocrCandidates.maxByOrNull { it.score })
        // A wrong top OCR line should not sink the whole lookup. The remaining
        // candidates are tried in order only when the previous one produced no
        // accepted match, so the common case still costs a single request.
        // A manual query is the user's correction: try it alone instead of
        // falling back to the text they just replaced.
        val textSeeds = if (manualSeed != null) {
            listOf(manualSeed)
        } else {
            buildList {
                add(seed)
                ocrCandidates
                    .sortedByDescending { it.score }
                    .take(MAX_TEXT_SEEDS)
                    .forEach { candidate ->
                        val alternative = seed(embeddedTags, candidate)
                        val duplicate = any {
                            it.tag.title == alternative.tag.title &&
                                it.tag.artist == alternative.tag.artist
                        }
                        if (!duplicate) add(alternative)
                    }
            }
        }
        var keyRejected = false
        var fingerprintTried = false
        var acrCloudKeyRejected = false
        var acrCloudTried = false

        suspend fun tryFingerprint(): IdentificationOutcome? {
            if (!networkEnabled || !fingerprintEnabled || !acoustIdConfigured) return null
            val fingerprintSeed = FingerprintSeed(
                title = seed.tag.title,
                artist = seed.tag.artist.takeIf { it.isNotBlank() },
                durationMs = trackDurationMs ?: durationMs,
            )
            val attempt = fingerprintSafely { fingerprintLookup(fingerprintSeed) }
            fingerprintTried = true
            keyRejected = attempt.keyRejected
            val ranked = MetadataMatcher.rank(
                // A manual run trusts the fingerprint over the text seed: OCR
                // may have produced a wrong title, which would score the real
                // fingerprint match down to nothing. With a blank query the
                // provider's own score decides.
                query = if (forceFingerprint) TagDraft() else seed.tag,
                matches = attempt.matches,
            ).accepted()
            return if (ranked.isEmpty()) {
                null
            } else {
                outcome(
                    seed = seed,
                    ranked = ranked,
                    rawOcrLines = rawOcrLines,
                    usedFingerprint = true,
                    fingerprintTried = true,
                    fingerprintKeyRejected = keyRejected,
                    trustMatch = forceFingerprint,
                )
            }
        }

        suspend fun tryAcrCloud(): IdentificationOutcome? {
            val recognize = audioLookup ?: return null
            if (!networkEnabled || !acrCloudConfigured) return null
            val attempt = audioSafely { recognize(trackDurationMs ?: durationMs) }
            acrCloudTried = true
            acrCloudKeyRejected = attempt.keyRejected
            val ranked = MetadataMatcher.rank(
                // A manual audio run trusts the recognizer over the text seed,
                // for the same reason a manual fingerprint run does.
                query = if (forceFingerprint) TagDraft() else seed.tag,
                matches = attempt.matches,
            ).accepted()
            return if (ranked.isEmpty()) {
                null
            } else {
                outcome(
                    seed = seed,
                    ranked = ranked,
                    rawOcrLines = rawOcrLines,
                    usedFingerprint = false,
                    fingerprintTried = fingerprintTried,
                    fingerprintKeyRejected = keyRejected,
                    usedAcrCloud = true,
                    acrCloudKeyRejected = acrCloudKeyRejected,
                    trustMatch = forceFingerprint,
                )
            }
        }

        suspend fun tryText(): IdentificationOutcome? {
            if (!networkEnabled) return null
            for (textSeed in textSeeds) {
                if (textSeed.tag.title.isBlank()) continue
                val lookupSeed = LookupSeed(
                    title = textSeed.tag.title,
                    artist = textSeed.tag.artist.takeIf { it.isNotBlank() },
                    album = textSeed.tag.album.takeIf { it.isNotBlank() },
                    durationMs = durationMs,
                )
                val matches = lookupSafely { lookup(lookupSeed) }
                val ranked = MetadataMatcher.rank(textSeed.tag, matches).accepted()
                if (ranked.isNotEmpty()) {
                    return outcome(
                        seed = textSeed,
                        ranked = ranked,
                        rawOcrLines = rawOcrLines,
                        usedFingerprint = false,
                        fingerprintTried = fingerprintTried,
                        fingerprintKeyRejected = keyRejected,
                    )
                }
            }
            return null
        }

        val result = if (forceFingerprint) {
            tryFingerprint() ?: tryAcrCloud() ?: tryText()
        } else {
            tryText() ?: tryFingerprint() ?: tryAcrCloud()
        }
        if (result != null) return result

        return IdentificationOutcome(
            tag = seed.tag,
            sources = seed.sources,
            alternatives = emptyList(),
            rawOcrLines = rawOcrLines,
            usedFingerprint = false,
            usedEmbeddedTags = seed.sources.containsValue(IdentificationSource.EMBEDDED_TAGS),
            fingerprintKeyRejected = keyRejected,
            fingerprintTried = fingerprintTried,
            acrCloudKeyRejected = acrCloudKeyRejected,
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

    private suspend fun audioSafely(
        block: suspend () -> AudioRecognitionAttempt,
    ): AudioRecognitionAttempt =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AudioRecognitionAttempt()
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
        fingerprintTried: Boolean = false,
        fingerprintKeyRejected: Boolean = false,
        usedAcrCloud: Boolean = false,
        acrCloudKeyRejected: Boolean = false,
        /**
         * A manual fingerprint run trusts the provider over the text seed, so
         * OCR-read fields are cleared before merging. Otherwise the merge
         * keeps the (possibly wrong) on-screen title and the fingerprint only
         * fills the blanks.
         */
        trustMatch: Boolean = false,
    ): IdentificationOutcome {
        val baseTag = if (trustMatch) seed.tag.withoutOcrFields(seed.sources) else seed.tag
        val baseSources = if (trustMatch) {
            seed.sources.filterValues { it != IdentificationSource.OCR_LATIN && it != IdentificationSource.OCR_HEBREW }
        } else {
            seed.sources
        }
        val merged = MetadataMerger.merge(
            base = baseTag,
            matches = ranked,
            baseSource = seed.defaultSource,
            baseFieldSources = baseSources,
        )
        return IdentificationOutcome(
            tag = merged.tag,
            sources = merged.sources,
            alternatives = ranked.drop(1),
            rawOcrLines = rawOcrLines,
            usedFingerprint = usedFingerprint,
            usedEmbeddedTags = merged.sources.containsValue(IdentificationSource.EMBEDDED_TAGS),
            fingerprintKeyRejected = fingerprintKeyRejected,
            fingerprintTried = fingerprintTried,
            usedAcrCloud = usedAcrCloud,
            acrCloudKeyRejected = acrCloudKeyRejected,
            bestMatch = ranked.firstOrNull(),
        )
    }

    private fun TagDraft.withoutOcrFields(
        sources: Map<TagField, IdentificationSource>,
    ): TagDraft {
        fun ocr(field: TagField): Boolean {
            val source = sources[field] ?: return false
            return source == IdentificationSource.OCR_LATIN || source == IdentificationSource.OCR_HEBREW
        }
        return copy(
            title = if (ocr(TagField.TITLE)) "" else title,
            artist = if (ocr(TagField.ARTIST)) "" else artist,
            album = if (ocr(TagField.ALBUM)) "" else album,
            albumArtist = if (ocr(TagField.ALBUM_ARTIST)) "" else albumArtist,
        )
    }

    private fun manualSeed(query: LookupSeed): Seed {
        val artist = query.artist?.trim().orEmpty()
        val album = query.album?.trim().orEmpty()
        val sources = linkedMapOf(TagField.TITLE to IdentificationSource.MANUAL)
        if (artist.isNotEmpty()) sources[TagField.ARTIST] = IdentificationSource.MANUAL
        if (album.isNotEmpty()) sources[TagField.ALBUM] = IdentificationSource.MANUAL
        return Seed(
            tag = TagDraft(
                title = query.title.trim(),
                artist = artist,
                album = album,
            ),
            sources = sources,
            defaultSource = IdentificationSource.MANUAL,
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

        /** How many OCR readings the text stage may try before giving up. */
        const val MAX_TEXT_SEEDS = 3
    }
}
