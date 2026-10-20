package io.github.rpn17m2g.cue.data.metadata

/** Extracts the first four digits of an ISO-ish date, or null when it is not a year. */
internal fun String?.toYear(): String? {
    val value = this?.trim() ?: return null
    if (value.length < 4) return null
    val year = value.take(4)
    return year.takeIf { candidate -> candidate.all(Char::isDigit) }
}

/** iTunes artwork URLs encode the requested size; ask for the 600x600 variant. */
internal fun String?.upscaledArtwork(): String? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value.replace(ITUNES_ARTWORK_SIZE, ITUNES_ARTWORK_SIZE_LARGE)
}

private const val ITUNES_ARTWORK_SIZE = "100x100bb"
private const val ITUNES_ARTWORK_SIZE_LARGE = "600x600bb"
