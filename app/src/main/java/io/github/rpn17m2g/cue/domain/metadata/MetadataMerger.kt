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
 * A provider match scoring at least [HIGH_CONFIDENCE_SIMILARITY] against the
 * draft may also replace fields currently sourced from OCR, because the online
 * result is more trustworthy than a noisy reading. Manual and embedded-tag
 * values are never replaced.
 */
object MetadataMerger {

    const val HIGH_CONFIDENCE_SIMILARITY = 0.85

    fun merge(
        base: TagDraft,
        matches: List<MetadataMatch>,
        baseSource: IdentificationSource = IdentificationSource.MANUAL,
        baseFieldSources: Map<TagField, IdentificationSource> = emptyMap(),
    ): TagMergeResult {
        val best = matches.maxByOrNull { it.score }
        val sources = linkedMapOf<TagField, IdentificationSource>()
        val highConfidence = best != null &&
            MetadataMatcher.score(base, best) >= HIGH_CONFIDENCE_SIMILARITY

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
            ),
            sources = sources,
        )
    }
}
