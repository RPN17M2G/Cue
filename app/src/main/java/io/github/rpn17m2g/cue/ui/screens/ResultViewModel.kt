package io.github.rpn17m2g.cue.ui.screens

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.data.output.SourceOutcome
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.processing.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProcessingState> = container.processingSession.state

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /**
     * The system delete request for the pending source, or `null` when the
     * source is not a MediaStore row (the in-app confirmation is then final).
     */
    fun deleteRequest(): IntentSender? = currentSong()
        ?.takeIf { it.sourceOutcome == SourceOutcome.PENDING }
        ?.sourceUri
        ?.let(container.sourceDeleter::deleteRequest)

    /** Records the result of the system delete dialog. */
    fun completeSystemDelete(deleted: Boolean) {
        markSource(if (deleted) SourceOutcome.DELETED else SourceOutcome.KEPT)
    }

    /** Deletes a non-MediaStore document behind the in-app confirmation. */
    fun deleteWithoutSystemDialog(onResult: (Boolean) -> Unit) {
        val song = currentSong()?.takeIf { it.sourceOutcome == SourceOutcome.PENDING } ?: return
        val uri = song.sourceUri ?: return
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                container.sourceDeleter.deleteDocument(uri)
            }
            markSource(if (deleted) SourceOutcome.DELETED else SourceOutcome.KEPT)
            onResult(deleted)
        }
    }

    fun keepSource() {
        markSource(SourceOutcome.KEPT)
    }

    private fun markSource(outcome: SourceOutcome) {
        val song = currentSong() ?: return
        container.processingSession.updateSong(song.copy(sourceOutcome = outcome))
    }

    private fun currentSong(): SavedSong? =
        (container.processingSession.state.value as? ProcessingState.Done)?.song

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
