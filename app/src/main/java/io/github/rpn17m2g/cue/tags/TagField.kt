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
    ;

    companion object {
        /** The fields a normal text-only edit updates; artwork is deliberately excluded. */
        val TEXT_FIELDS: Set<TagField> = setOf(
            TITLE,
            ARTIST,
            ALBUM,
            ALBUM_ARTIST,
            YEAR,
            GENRE,
            TRACK_NUMBER,
        )

        val ALL_FIELDS: Set<TagField> = entries.toSet()
    }
}
