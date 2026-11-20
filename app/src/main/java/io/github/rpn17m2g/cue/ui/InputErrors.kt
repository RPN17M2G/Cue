package io.github.rpn17m2g.cue.ui

import android.content.Context
import android.text.format.Formatter
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.input.InsufficientStorageException

/**
 * The pair the error screen shows for a document that could not be opened.
 *
 * Running out of room is the one failure the user can act on, so it gets a
 * sentence with the two sizes instead of the generic "could not be opened" plus
 * a provider's message. Everything else keeps the technical detail as before.
 */
fun inputError(context: Context, e: Exception): Pair<String, String?> = when (e) {
    is InsufficientStorageException -> context.getString(
        R.string.error_no_space,
        Formatter.formatShortFileSize(context, e.neededBytes),
        Formatter.formatShortFileSize(context, e.availableBytes),
    ) to null
    else -> context.getString(R.string.error_input_failed) to e.message
}
