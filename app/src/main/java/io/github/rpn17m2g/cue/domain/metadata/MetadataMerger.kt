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
 */
object MetadataMerger {

    fun merge(
        base: TagDraft,
        matches: List<MetadataMatch>,
        baseSource: IdentificationSource = IdentificationSource.MANUAL,
        baseFieldSources: Map<TagField, IdentificationSource> = emptyMap(),
    ): TagMergeResult {
        val best = matches.maxByOrNull { it.score }
        val sources = linkedMapOf<TagField, IdentificationSource>()

        fun baseSourceOf(field: TagField): IdentificationSource = baseFieldSources[field] ?: baseSource
        fun matchSource(): IdentificationSource = best?.source ?: baseSource

        val title = if (base.title.isNotBlank()) {
            sources[TagField.TITLE] = baseSourceOf(TagField.TITLE)
            base.title
        } else {
            best?.title?.takeIf { it.isNotBlank() }?.also { sources[TagField.TITLE] = matchSource() }.orEmpty()
        }
        val artist = if (base.artist.isNotBlank()) {
            sources[TagField.ARTIST] = baseSourceOf(TagField.ARTIST)
            base.artist
        } else {
            best?.artist?.takeIf { it.isNotBlank() }?.also { sources[TagField.ARTIST] = matchSource() }.orEmpty()
        }
        val album = if (base.album.isNotBlank()) {
            sources[TagField.ALBUM] = baseSourceOf(TagField.ALBUM)
            base.album
        } else {
            best?.album?.takeIf { it.isNotBlank() }?.also { sources[TagField.ALBUM] = matchSource() }.orEmpty()
        }
        val albumArtist = if (base.albumArtist.isNotBlank()) {
            sources[TagField.ALBUM_ARTIST] = baseSourceOf(TagField.ALBUM_ARTIST)
            base.albumArtist
        } else {
            best?.albumArtist?.takeIf { it.isNotBlank() }
                ?.also { sources[TagField.ALBUM_ARTIST] = matchSource() }.orEmpty()
        }
        val year = if (!base.year.isNullOrBlank()) {
            sources[TagField.YEAR] = baseSourceOf(TagField.YEAR)
            base.year
        } else {
            best?.year?.takeIf { it.isNotBlank() }?.also { sources[TagField.YEAR] = matchSource() }
        }
        val genre = if (!base.genre.isNullOrBlank()) {
            sources[TagField.GENRE] = baseSourceOf(TagField.GENRE)
            base.genre
        } else {
            best?.genre?.takeIf { it.isNotBlank() }?.also { sources[TagField.GENRE] = matchSource() }
        }
        val trackNumber = if (base.trackNumber != null) {
            sources[TagField.TRACK_NUMBER] = baseSourceOf(TagField.TRACK_NUMBER)
            base.trackNumber
        } else {
            best?.trackNumber?.also { sources[TagField.TRACK_NUMBER] = matchSource() }
        }
        val artwork = if (base.artwork != null) {
            sources[TagField.ARTWORK] = baseSourceOf(TagField.ARTWORK)
            base.artwork
        } else {
            best?.artwork?.also { sources[TagField.ARTWORK] = matchSource() }
        }

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
