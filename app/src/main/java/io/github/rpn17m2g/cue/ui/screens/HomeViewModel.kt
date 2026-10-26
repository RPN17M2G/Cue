package io.github.rpn17m2g.cue.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    fun resolve(uri: Uri, onResolved: (String) -> Unit, onError: () -> Unit) {
        if (_resolving.value) return
        if (container.processingSession.isRunning) {
            container.errorReporter.report(container.appContext.getString(R.string.error_busy))
            onError()
            return
        }
        viewModelScope.launch {
            _resolving.value = true
            container.errorReporter.clear()
            try {
                container.sessionRepository.current.value?.let {
                    container.inputResolver.cleanup(it.input)
                }
                container.sessionRepository.clear()
                val input = container.inputResolver.resolve(uri)
                val session = container.sessionRepository.create(input)
                onResolved(session.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                container.errorReporter.report(
                    container.appContext.getString(R.string.error_input_failed),
                    e.message,
                )
                onError()
            } finally {
                _resolving.value = false
            }
        }
    }
}
