package io.github.rpn17m2g.cue.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimMath
import io.github.rpn17m2g.cue.processing.ProcessingJob
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.processing.SongProcessingService
import io.github.rpn17m2g.cue.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewUiState(
    val loading: Boolean = true,
    val input: ResolvedInput? = null,
    val draft: TagDraft = TagDraft(),
    val quality: QualityPreset = QualityPreset.V0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val durationMs: Long? = null,
    val errorRes: Int? = null,
    val saving: Boolean = false,
) {
    val hasTrim: Boolean get() = durationMs != null && durationMs > TrimMath.MIN_DURATION_MS

    val effectiveDurationMs: Long?
        get() = TrimMath.effectiveDurationMs(
            TrimMath.trimRange(trimStartMs, trimEndMs, durationMs),
            durationMs,
        )
}

class ReviewViewModel(
    private val container: AppContainer,
    private val inputId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private var settings = AppSettings()

    init {
        viewModelScope.launch {
            container.processingSession.state.collect { processing ->
                if (processing !is ProcessingState.Running) {
                    _state.update { it.copy(saving = false) }
                }
            }
        }
        viewModelScope.launch {
            val session = container.sessionRepository.find(inputId)
            if (session == null) {
                _state.update {
                    it.copy(loading = false, errorRes = R.string.review_session_missing)
                }
                return@launch
            }
            settings = container.settingsRepository.settings.first()
            val input = session.input
            val seeded = readEmbeddedTags(input)
            val duration = input.durationMs
            _state.value = ReviewUiState(
                loading = false,
                input = input,
                draft = seeded ?: TagDraft(title = defaultTitle(input)),
                quality = settings.defaultQuality,
                trimStartMs = 0L,
                trimEndMs = duration ?: 0L,
                durationMs = duration,
            )
        }
    }

    fun updateDraft(transform: (TagDraft) -> TagDraft) {
        _state.update { it.copy(draft = transform(it.draft), errorRes = null) }
    }

    fun setTrim(startMs: Long, endMs: Long) {
        _state.update { current ->
            val duration = current.durationMs ?: return@update current
            val window = TrimMath.clampWindow(startMs, endMs, duration)
            current.copy(trimStartMs = window.first, trimEndMs = window.last)
        }
    }

    fun setQuality(quality: QualityPreset) {
        _state.update { it.copy(quality = quality) }
    }

    fun save(onStarted: (String) -> Unit) {
        val current = _state.value
        val input = current.input ?: return
        if (current.saving) return
        if (container.processingSession.isRunning) {
            _state.update { it.copy(errorRes = R.string.error_busy) }
            return
        }
        val year = current.draft.year?.trim()?.takeIf { it.isNotEmpty() }
        if (year != null && !YEAR_PATTERN.matches(year)) {
            _state.update { it.copy(errorRes = R.string.review_year_invalid) }
            return
        }
        val job = ProcessingJob(
            input = input,
            draft = current.draft.copy(year = year),
            trim = TrimMath.trimRange(current.trimStartMs, current.trimEndMs, current.durationMs),
            quality = current.quality,
            outputFolder = settings.outputFolder,
            filenameTemplate = settings.filenameTemplate,
        )
        _state.update { it.copy(saving = true) }
        container.errorReporter.clear()
        container.sessionRepository.remember(job)
        container.processingSession.reset()
        SongProcessingService.start(container.appContext, job)
        onStarted(input.id)
    }

    private suspend fun readEmbeddedTags(input: ResolvedInput): TagDraft? {
        if (input.kind != InputKind.MP3) return null
        return try {
            withContext(Dispatchers.IO) {
                container.tagStore.read(File(input.path))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultTitle(input: ResolvedInput): String =
        input.displayName.substringBeforeLast('.', input.displayName).trim()

    private companion object {
        val YEAR_PATTERN = Regex("""^\d{4}(?:-\d{2}(?:-\d{2})?)?$""")
    }
}
