package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.TrimRange

/**
 * A writable MP3 can be edited in place: tags only when the audio is untouched,
 * or a trim that re-encodes and replaces the source document when the user
 * asked to keep no copy. Everything else publishes a new file.
 */
object ProcessingModeSelector {

    fun mode(
        input: ResolvedInput,
        trim: TrimRange?,
        replaceOriginal: Boolean = false,
    ): ProcessingMode = when {
        input.kind != InputKind.MP3 || !input.canEditInPlace ->
            ProcessingMode.CONVERT_AND_SAVE
        trim == null -> ProcessingMode.EDIT_IN_PLACE
        replaceOriginal -> ProcessingMode.TRIM_IN_PLACE
        else -> ProcessingMode.CONVERT_AND_SAVE
    }
}
