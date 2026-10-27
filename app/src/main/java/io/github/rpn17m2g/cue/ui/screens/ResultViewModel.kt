package io.github.rpn17m2g.cue.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.processing.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProcessingState> = container.processingSession.state

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /** Reopens Review for the just-saved file; the service will edit it in place. */
    fun editTags(song: SavedSong, onReady: (String) -> Unit, onError: () -> Unit) {
        if (_editing.value) return
        viewModelScope.launch {
            _editing.value = true
            try {
                val input = container.inputResolver.resolve(Uri.parse(song.uri))
                container.sessionRepository.current.value?.let {
                    container.inputResolver.cleanup(it.input)
                }
                container.sessionRepository.clear()
                val session = container.sessionRepository.create(input)
                onReady(session.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                container.errorReporter.report(
                    container.appContext.getString(R.string.error_input_failed),
                    e.message,
                )
                onError()
            } finally {
                _editing.value = false
            }
        }
    }

    fun processAnother() {
        container.sessionRepository.current.value?.let {
            container.inputResolver.cleanup(it.input)
        }
        container.sessionRepository.clear()
        container.inputResolver.releasePersistedPermissions()
        container.processingSession.reset()
        container.errorReporter.clear()
    }
}
