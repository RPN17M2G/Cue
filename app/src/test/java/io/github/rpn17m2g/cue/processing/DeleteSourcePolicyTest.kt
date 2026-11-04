package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.output.SourceOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delete-source rules are the safety boundary around a destructive action,
 * so they are pinned here rather than left to the two call sites.
 */
class DeleteSourcePolicyTest {

    @Test
    fun `a video is offered only when the setting is on`() {
        assertTrue(DeleteSourcePolicy.offered(InputKind.VIDEO, enabled = true))
        assertFalse(DeleteSourcePolicy.offered(InputKind.VIDEO, enabled = false))
    }

    @Test
    fun `an mp3 is never offered`() {
        assertFalse(DeleteSourcePolicy.offered(InputKind.MP3, enabled = true))
        assertFalse(DeleteSourcePolicy.offered(InputKind.MP3, enabled = false))
    }

    @Test
    fun `a requested video delete waits for the saved screen`() {
        assertEquals(
            SourceOutcome.PENDING,
            DeleteSourcePolicy.outcomeFor(InputKind.VIDEO, requested = true),
        )
        assertEquals(
            SourceOutcome.NONE,
            DeleteSourcePolicy.outcomeFor(InputKind.VIDEO, requested = false),
        )
    }

    @Test
    fun `an mp3 delete request is dropped`() {
        assertEquals(
            SourceOutcome.NONE,
            DeleteSourcePolicy.outcomeFor(InputKind.MP3, requested = true),
        )
    }
}
