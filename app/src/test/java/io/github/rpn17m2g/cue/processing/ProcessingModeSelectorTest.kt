package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingModeSelectorTest {

    @Test
    fun `a writable mp3 without a trim is edited in place`() {
        assertEquals(
            ProcessingMode.EDIT_IN_PLACE,
            ProcessingModeSelector.mode(mp3(canEditInPlace = true), trim = null),
        )
    }

    @Test
    fun `a trim always produces a new file`() {
        assertEquals(
            ProcessingMode.CONVERT_AND_SAVE,
            ProcessingModeSelector.mode(
                mp3(canEditInPlace = true),
                trim = TrimRange(1_000, 5_000),
            ),
        )
    }

    @Test
    fun `an mp3 without write access is copied and converted`() {
        assertEquals(
            ProcessingMode.CONVERT_AND_SAVE,
            ProcessingModeSelector.mode(mp3(canEditInPlace = false), trim = null),
        )
    }

    @Test
    fun `video is always converted`() {
        assertEquals(
            ProcessingMode.CONVERT_AND_SAVE,
            ProcessingModeSelector.mode(
                input(kind = InputKind.VIDEO, canEditInPlace = true),
                trim = null,
            ),
        )
    }

    private fun mp3(canEditInPlace: Boolean) =
        input(kind = InputKind.MP3, canEditInPlace = canEditInPlace)

    private fun input(kind: InputKind, canEditInPlace: Boolean) = ResolvedInput(
        id = "input",
        uri = "content://input",
        path = "/tmp/input",
        displayName = "input",
        mimeType = null,
        kind = kind,
        durationMs = 10_000,
        canEditInPlace = canEditInPlace,
    )
}
