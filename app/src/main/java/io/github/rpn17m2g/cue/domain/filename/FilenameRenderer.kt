package io.github.rpn17m2g.cue.domain.filename

import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.util.Locale

/**
 * Renders a safe output filename from an editable tag draft.
 *
 * The result has no extension and no path separators; callers add the container
 * extension once the file is written.
 */
object FilenameRenderer {

    const val DEFAULT_TEMPLATE = "{artist} - {title}"
    const val FALLBACK_NAME = "Unknown track"
    const val MAX_LENGTH = 120

    private val TOKEN = Regex("\\{([^{}]*)\\}")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val ILLEGAL = Regex("[<>:\"/\\\\|?*\\p{Cntrl}]")
    private val DUPLICATE_SEPARATOR = Regex("(?:\\s*[-\\u2013\\u2014|\\u2022]+\\s*){2,}")

    private fun isSeparator(char: Char): Boolean =
        char.isWhitespace() || char == '-' || char == '\u2013' || char == '\u2014' ||
            char == '|' || char == '\u2022'

    fun render(draft: TagDraft, template: String = DEFAULT_TEMPLATE): String {
        val substituted = TOKEN.replace(template) { match -> valueOf(match.groupValues[1], draft) }
        val collapsed = substituted.replace(WHITESPACE, " ").trim()
        val deduplicated = DUPLICATE_SEPARATOR.replace(collapsed, " - ")
        return sanitize(deduplicated.trim(::isSeparator))
    }

    fun sanitize(value: String): String {
        val replaced = ILLEGAL.replace(value, " ")
        val collapsed = replaced.replace(WHITESPACE, " ").trim { it == ' ' || it == '.' || it == '\u00A0' }
        val capped = collapsed.take(MAX_LENGTH).trim { it == ' ' || it == '.' || it == '\u00A0' }
        return capped.ifBlank { FALLBACK_NAME }
    }

    private fun valueOf(token: String, draft: TagDraft): String = when (token.lowercase(Locale.ROOT)) {
        "artist" -> draft.artist
        "title" -> draft.title
        "album" -> draft.album
        "track" -> draft.trackNumber?.toString().orEmpty()
        "year" -> draft.year.orEmpty()
        else -> ""
    }
}
