package io.github.rpn17m2g.cue.ui.screens

import androidx.lifecycle.ViewModel
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.processing.SongProcessingService

class ErrorViewModel(private val container: AppContainer) : ViewModel() {

    val processingState = container.processingSession.state
    val report = container.errorReporter.report

    fun retry(): String? {
        val job = container.sessionRepository.lastJob ?: return null
        container.errorReporter.clear()
        container.processingSession.reset()
        SongProcessingService.start(container.appContext, job)
        return job.inputId
    }

    fun clearReport() {
        container.errorReporter.clear()
    }
}
