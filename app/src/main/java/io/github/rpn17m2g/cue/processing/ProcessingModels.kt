package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange
import io.github.rpn17m2g.cue.tags.TagField

sealed interface ProcessingStage {
    data object Preparing : ProcessingStage
    data object Converting : ProcessingStage
    data object Tagging : ProcessingStage
    data object Saving : ProcessingStage
}

/** Tag-only edits rewrite the source document; everything else publishes a new file. */
enum class ProcessingMode {
    CONVERT_AND_SAVE,
    EDIT_IN_PLACE,
}

data class ProcessingJob(
    val input: ResolvedInput,
    val draft: TagDraft,
    val trim: TrimRange?,
    val quality: QualityPreset,
    val outputFolder: String,
    val filenameTemplate: String,
    val mode: ProcessingMode = ProcessingMode.CONVERT_AND_SAVE,
    val changedFields: Set<TagField> = emptySet(),
    /**
     * Set by Review for videos only. The service never deletes on its own: it
     * marks the saved song as pending, and the Saved screen asks the OS.
     */
    val deleteSource: Boolean = false,
) {
    val inputId: String get() = input.id

    val needsConversion: Boolean get() = mode == ProcessingMode.CONVERT_AND_SAVE
}

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(val stage: ProcessingStage, val progress: Float) : ProcessingState
    data class Done(val song: SavedSong) : ProcessingState
    data class Failed(val reason: String, val details: String?) : ProcessingState
}

class ProcessingException(
    message: String,
    val details: List<String> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)
