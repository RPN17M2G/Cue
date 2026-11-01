package io.github.rpn17m2g.cue.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.library.LibraryEntry
import io.github.rpn17m2g.cue.data.library.LibraryTab
import io.github.rpn17m2g.cue.data.library.MediaPermissions
import io.github.rpn17m2g.cue.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val tab: LibraryTab = LibraryTab.RECENT,
    val permissions: MediaPermissions = MediaPermissions(video = false, audio = false),
    val entries: List<LibraryEntry> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private var refreshJob: Job? = null

    fun selectTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab) }
    }

    /**
     * Re-reads the grants and the MediaStore. Called on every resume, because
     * the user may have recorded a new video, granted access in system
     * settings, or Cue may have just written a new MP3.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val permissions = container.mediaLibrary.permissions()
            _state.update { it.copy(permissions = permissions, loading = true) }
            val entries = if (permissions.any) container.mediaLibrary.load() else emptyList()
            _state.update { it.copy(entries = entries, loading = false, loaded = true) }
        }
    }

    suspend fun thumbnail(uri: Uri): Bitmap? = container.thumbnailStore.load(uri)

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
                container.inputResolver.releasePersistedPermissions()
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
