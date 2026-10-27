package io.github.rpn17m2g.cue.domain.model

import io.github.rpn17m2g.cue.tags.TagField

/**
 * Fields whose edited value differs from [base]. An in-place edit writes only
 * these fields, leaving everything else (including artwork) untouched.
 */
fun TagDraft.changedFieldsFrom(base: TagDraft): Set<TagField> = buildSet {
    if (title != base.title) add(TagField.TITLE)
    if (artist != base.artist) add(TagField.ARTIST)
    if (album != base.album) add(TagField.ALBUM)
    if (albumArtist != base.albumArtist) add(TagField.ALBUM_ARTIST)
    if (yearKey() != base.yearKey()) add(TagField.YEAR)
    if (genre != base.genre) add(TagField.GENRE)
    if (trackNumber != base.trackNumber || trackTotal != base.trackTotal) {
        add(TagField.TRACK_NUMBER)
    }
    if (artwork != base.artwork) add(TagField.ARTWORK)
}

/**
 * Verifies the fields that were written. ID3 years are truncated to four digits
 * on write, so the comparison uses the same normalized key.
 */
fun TagDraft.matchesWritten(expected: TagDraft, fields: Set<TagField>): Boolean = when {
    TagField.TITLE in fields && title != expected.title -> false
    TagField.ARTIST in fields && artist != expected.artist -> false
    TagField.ALBUM in fields && album != expected.album -> false
    TagField.ALBUM_ARTIST in fields && albumArtist != expected.albumArtist -> false
    TagField.YEAR in fields && yearKey() != expected.yearKey() -> false
    TagField.GENRE in fields && genre != expected.genre -> false
    TagField.TRACK_NUMBER in fields &&
        trackNumber != expected.trackNumber -> false
    TagField.TRACK_NUMBER in fields &&
        expected.trackTotal != null &&
        trackTotal != expected.trackTotal -> false
    TagField.ARTWORK in fields && artwork != expected.artwork -> false
    else -> true
}

private fun TagDraft.yearKey(): String? = year
    ?.trim()
    ?.take(4)
    ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
