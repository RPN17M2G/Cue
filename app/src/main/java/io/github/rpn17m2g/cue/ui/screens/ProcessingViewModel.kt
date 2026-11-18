package io.github.rpn17m2g.cue.ui.screens

import androidx.lifecycle.ViewModel
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.processing.ProcessingMode
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.processing.SongProcessingService
import kotlinx.coroutines.flow.StateFlow

class ProcessingViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProcessingState> = container.processingSession.state

    val inPlace: Boolean
        get() = container.sessionRepository.lastJob?.mode
            ?.let { it != ProcessingMode.CONVERT_AND_SAVE } == true

    fun cancel() {
        SongProcessingService.cancel(container.appContext)
    }
}
