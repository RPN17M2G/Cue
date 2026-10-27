package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.TrimRange

/**
 * A tag-only edit is possible only for an MP3 whose source grants write access
 * and whose audio is untouched. Any trim means re-encoding into a new file.
 */
object ProcessingModeSelector {

    fun mode(input: ResolvedInput, trim: TrimRange?): ProcessingMode =
        if (input.kind == InputKind.MP3 && input.canEditInPlace && trim == null) {
            ProcessingMode.EDIT_IN_PLACE
        } else {
            ProcessingMode.CONVERT_AND_SAVE
        }
}
