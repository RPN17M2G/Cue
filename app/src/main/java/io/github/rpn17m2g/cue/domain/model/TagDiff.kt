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
    if (lyrics.orEmpty() != base.lyrics.orEmpty() ||
        !syncedLyrics.sameTimedLyrics(base.syncedLyrics)
    ) {
        add(TagField.LYRICS)
    }
    if (bpm != base.bpm) add(TagField.BPM)
    if (isrc.orEmpty() != base.isrc.orEmpty()) add(TagField.ISRC)
    if (label.orEmpty() != base.label.orEmpty()) add(TagField.LABEL)
    if (musicBrainzKey() != base.musicBrainzKey()) add(TagField.MUSICBRAINZ)
    if (!replayGainDb.replayGainMatches(base.replayGainDb)) add(TagField.REPLAY_GAIN)
}

/**
 * Verifies the fields that were written. ID3 years are truncated to four digits
 * on write and blank text is stored as a removed frame, so both sides are
 * normalized before they are compared.
 */
fun TagDraft.matchesWritten(expected: TagDraft, fields: Set<TagField>): Boolean = when {
    TagField.TITLE in fields && !title.sameTagText(expected.title) -> false
    TagField.ARTIST in fields && !artist.sameTagText(expected.artist) -> false
    TagField.ALBUM in fields && !album.sameTagText(expected.album) -> false
    TagField.ALBUM_ARTIST in fields && !albumArtist.sameTagText(expected.albumArtist) -> false
    TagField.YEAR in fields && yearKey() != expected.yearKey() -> false
    TagField.GENRE in fields && !genre.sameTagText(expected.genre) -> false
    TagField.TRACK_NUMBER in fields &&
        trackNumber != expected.trackNumber -> false
    TagField.TRACK_NUMBER in fields &&
        expected.trackTotal != null &&
        trackTotal != expected.trackTotal -> false
    TagField.ARTWORK in fields && artwork != expected.artwork -> false
    TagField.LYRICS in fields &&
        !lyrics.sameTagText(expected.lyrics) -> false
    TagField.LYRICS in fields &&
        !syncedLyrics.sameTimedLyrics(expected.syncedLyrics) -> false
    TagField.BPM in fields && bpm != expected.bpm -> false
    TagField.ISRC in fields && !isrc.sameTagText(expected.isrc) -> false
    TagField.LABEL in fields && !label.sameTagText(expected.label) -> false
    TagField.MUSICBRAINZ in fields && musicBrainzKey() != expected.musicBrainzKey() -> false
    TagField.REPLAY_GAIN in fields &&
        !replayGainDb.replayGainMatches(expected.replayGainDb) -> false
    else -> true
}

/** The writer drops a blank frame, so `" "` and `""` both read back as absent. */
private fun String?.sameTagText(other: String?): Boolean =
    orEmpty().trim() == other.orEmpty().trim()

/**
 * SYLT is read back as canonical LRC, so the same timings may differ in
 * spacing or fraction width. Compare at the precision the tag actually
 * round-trips through, which is the centisecond LRC form.
 */
private fun String?.sameTimedLyrics(other: String?): Boolean {
    val left = this?.takeIf { it.isNotBlank() }?.let(::parseLrc).orEmpty()
    val right = other?.takeIf { it.isNotBlank() }?.let(::parseLrc).orEmpty()
    return left.toLrc() == right.toLrc()
}

/**
 * ReplayGain is written with one decimal; a provider may hand over more
 * precision, so compare at the precision the tag actually stores.
 */
private fun Double?.replayGainMatches(other: Double?): Boolean {
    if (this == null || other == null) return this == other
    return kotlin.math.abs(this - other) < 0.05
}

/** The three MusicBrainz identifiers are one logical tag field. */
private fun TagDraft.musicBrainzKey(): String =
    listOf(recordingMbid, releaseMbid, artistMbid)
        .joinToString("|") { it.orEmpty().trim() }

private fun TagDraft.yearKey(): String? = year
    ?.trim()
    ?.take(4)
    ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
