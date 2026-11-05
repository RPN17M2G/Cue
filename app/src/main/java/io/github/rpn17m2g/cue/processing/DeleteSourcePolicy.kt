package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.output.SourceOutcome

/**
 * The delete-the-source decision table, kept in one place so the Review default
 * and the finished job cannot disagree.
 *
 * Videos only. An MP3 that is edited in place *is* the saved file, so there is
 * nothing to delete, and the source of a converted MP3 may be the only copy of
 * a different asset.
 */
object DeleteSourcePolicy {

    /** Whether Review may offer to delete [kind]'s source at all. */
    fun offered(kind: InputKind, enabled: Boolean): Boolean =
        enabled && kind == InputKind.VIDEO

    /** What the finished job hands to the Saved screen. */
    fun outcomeFor(kind: InputKind, requested: Boolean): SourceOutcome =
        if (offered(kind, requested)) SourceOutcome.PENDING else SourceOutcome.NONE
}
