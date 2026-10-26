package io.github.rpn17m2g.cue.processing

import io.github.rpn17m2g.cue.data.output.SavedSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The single in-process job state observed by the processing, result, and error screens. */
class ProcessingSession {

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value is ProcessingState.Running

    fun running(stage: ProcessingStage, progress: Float) {
        _state.value = ProcessingState.Running(stage, progress.coerceIn(0f, 1f))
    }

    fun done(song: SavedSong) {
        _state.value = ProcessingState.Done(song)
    }

    fun failed(reason: String, details: String?) {
        _state.value = ProcessingState.Failed(reason, details)
    }

    fun reset() {
        _state.value = ProcessingState.Idle
    }
}
