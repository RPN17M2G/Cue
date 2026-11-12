package io.github.rpn17m2g.cue.domain.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft

data class TagMergeResult(
    val tag: TagDraft,
    val sources: Map<TagField, IdentificationSource>,
)

/**
 * Fills blank draft fields from the highest-scoring match while never
 * overwriting a value the user or an earlier stage already supplied.
 *
 * A provider match is high confidence when its title similarity is at least
 * [HIGH_CONFIDENCE_SIMILARITY]. If both artists are present, a near-exact title
 * is enough on its own; otherwise the artists must also reach
 * [MIN_ARTIST_SIMILARITY]. A high-confidence match may replace fields currently
 * sourced from OCR, because the online result is more trustworthy than a noisy
 * reading. Manual and embedded-tag values are never replaced.
 */
object MetadataMerger {

    const val HIGH_CONFIDENCE_SIMILARITY = 0.85
    private const val NEAR_EXACT_TITLE_SIMILARITY = 0.95
    private const val MIN_ARTIST_SIMILARITY = 0.5

    fun merge(
        base: TagDraft,
        matches: List<MetadataMatch>,
        baseSource: IdentificationSource = IdentificationSource.MANUAL,
        baseFieldSources: Map<TagField, IdentificationSource> = emptyMap(),
    ): TagMergeResult {
        val best = matches.maxByOrNull { it.score }
        val sources = linkedMapOf<TagField, IdentificationSource>()
        val highConfidence = best != null && isHighConfidence(base, best)

        fun baseSourceOf(field: TagField): IdentificationSource = baseFieldSources[field] ?: baseSource
        fun matchSource(): IdentificationSource = best?.source ?: baseSource
        fun isOcr(source: IdentificationSource): Boolean =
            source == IdentificationSource.OCR_LATIN || source == IdentificationSource.OCR_HEBREW

        fun <T> choose(
            field: TagField,
            baseValue: T?,
            isBlank: (T) -> Boolean,
            matchValue: T?,
        ): T? {
            if (baseValue != null && !isBlank(baseValue)) {
                val currentSource = baseSourceOf(field)
                if (highConfidence && isOcr(currentSource) && matchValue != null && !isBlank(matchValue)) {
                    sources[field] = matchSource()
                    return matchValue
                }
                sources[field] = currentSource
                return baseValue
            }
            if (matchValue != null && !isBlank(matchValue)) {
                sources[field] = matchSource()
                return matchValue
            }
            return null
        }

        val title = choose(TagField.TITLE, base.title, String::isBlank, best?.title).orEmpty()
        val artist = choose(TagField.ARTIST, base.artist, String::isBlank, best?.artist).orEmpty()
        val album = choose(TagField.ALBUM, base.album, String::isBlank, best?.album).orEmpty()
        val albumArtist = choose(
            TagField.ALBUM_ARTIST,
            base.albumArtist,
            String::isBlank,
            best?.albumArtist,
        ).orEmpty()
        val year = choose(TagField.YEAR, base.year, String::isBlank, best?.year)
        val genre = choose(TagField.GENRE, base.genre, String::isBlank, best?.genre)
        val trackNumber = choose(TagField.TRACK_NUMBER, base.trackNumber, { false }, best?.trackNumber)
        val artwork = choose(TagField.ARTWORK, base.artwork, { false }, best?.artwork)
        val lyrics = choose(TagField.LYRICS, base.lyrics, String::isBlank, null)
        val bpm = choose(TagField.BPM, base.bpm, { it <= 0 }, best?.bpm)
        val isrc = choose(TagField.ISRC, base.isrc, String::isBlank, best?.isrc)
        val label = choose(TagField.LABEL, base.label, String::isBlank, best?.label)
        val replayGain = choose(
            TagField.REPLAY_GAIN,
            base.replayGainDb,
            { false },
            best?.replayGainDb,
        )
        val recordingMbid = choose(
            TagField.MUSICBRAINZ,
            base.recordingMbid,
            String::isBlank,
            best?.recordingMbid,
        )
        val releaseMbid = choose(
            TagField.MUSICBRAINZ,
            base.releaseMbid,
            String::isBlank,
            best?.releaseMbid,
        )
        val artistMbid = choose(
            TagField.MUSICBRAINZ,
            base.artistMbid,
            String::isBlank,
            best?.artistMbid,
        )

        return TagMergeResult(
            tag = TagDraft(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                year = year,
                genre = genre,
                trackNumber = trackNumber,
                artwork = artwork,
                trackTotal = base.trackTotal,
                lyrics = lyrics,
                bpm = bpm,
                isrc = isrc,
                label = label,
                replayGainDb = replayGain,
                recordingMbid = recordingMbid,
                releaseMbid = releaseMbid,
                artistMbid = artistMbid,
            ),
            sources = sources,
        )
    }

    private fun isHighConfidence(base: TagDraft, match: MetadataMatch): Boolean {
        val titleSimilarity = Similarity.textSimilarity(base.title, match.title)
        if (titleSimilarity < HIGH_CONFIDENCE_SIMILARITY) return false
        if (base.artist.isBlank() || match.artist.isBlank()) return true
        if (titleSimilarity >= NEAR_EXACT_TITLE_SIMILARITY) return true
        return Similarity.textSimilarity(base.artist, match.artist) >= MIN_ARTIST_SIMILARITY
    }
}
