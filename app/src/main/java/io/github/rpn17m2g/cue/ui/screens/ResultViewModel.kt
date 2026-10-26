package io.github.rpn17m2g.cue.ui.screens

import androidx.lifecycle.ViewModel
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.processing.ProcessingState
import kotlinx.coroutines.flow.StateFlow

class ResultViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProcessingState> = container.processingSession.state

    fun processAnother() {
        container.sessionRepository.current.value?.let {
            container.inputResolver.cleanup(it.input)
        }
        container.sessionRepository.clear()
        container.processingSession.reset()
        container.errorReporter.clear()
    }
}
