package io.github.rpn17m2g.cue.tags

/** A single editable ID3 field, used to limit which values a write updates. */
enum class TagField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    GENRE,
    TRACK_NUMBER,
    ARTWORK,
    LYRICS,
    BPM,
    ISRC,
    LABEL,
    MUSICBRAINZ,
    REPLAY_GAIN,
    ;

    companion object {
        /**
         * The fields a normal text-only edit updates; artwork is deliberately
         * excluded. Enrichment fields are included because they are part of the
         * same draft and must survive a re-encode or an in-place edit.
         */
        val TEXT_FIELDS: Set<TagField> = setOf(
            TITLE,
            ARTIST,
            ALBUM,
            ALBUM_ARTIST,
            YEAR,
            GENRE,
            TRACK_NUMBER,
            LYRICS,
            BPM,
            ISRC,
            LABEL,
            MUSICBRAINZ,
            REPLAY_GAIN,
        )

        val ALL_FIELDS: Set<TagField> = entries.toSet()
    }
}
