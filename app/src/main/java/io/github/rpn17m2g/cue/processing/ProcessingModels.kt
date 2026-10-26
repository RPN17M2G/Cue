package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange

sealed interface ProcessingStage {
    data object Preparing : ProcessingStage
    data object Converting : ProcessingStage
    data object Tagging : ProcessingStage
    data object Saving : ProcessingStage
}

data class ProcessingJob(
    val input: ResolvedInput,
    val draft: TagDraft,
    val trim: TrimRange?,
    val quality: QualityPreset,
    val outputFolder: String,
    val filenameTemplate: String,
) {
    val inputId: String get() = input.id
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
