package io.github.rpn17m2g.cue.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.metadata.ArtworkCandidate
import io.github.rpn17m2g.cue.data.metadata.MetadataQuery
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.domain.identify.SongIdentification
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimMath
import io.github.rpn17m2g.cue.domain.model.TrimRange
import io.github.rpn17m2g.cue.domain.model.changedFieldsFrom
import io.github.rpn17m2g.cue.processing.ProcessingJob
import io.github.rpn17m2g.cue.processing.ProcessingMode
import io.github.rpn17m2g.cue.processing.ProcessingModeSelector
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.processing.SongProcessingService
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.prefersHebrew
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val identifying: Boolean = false,
    val sources: Map<TagField, IdentificationSource> = emptyMap(),
    val alternatives: List<MetadataMatch> = emptyList(),
    val searchResults: List<MetadataMatch> = emptyList(),
    val searchCompleted: Boolean = false,
    val searching: Boolean = false,
    val searchOffline: Boolean = false,
    val rawOcrLines: List<String> = emptyList(),
    val ocrAttempted: Boolean = false,
    val ocrFailed: Boolean = false,
    val fingerprintAvailable: Boolean = false,
    val fingerprintKeyRejected: Boolean = false,
    val artworkPickerOpen: Boolean = false,
    val artworkResults: List<ArtworkCandidate> = emptyList(),
    val artworkSearching: Boolean = false,
    val artworkSearchCompleted: Boolean = false,
    val artworkOffline: Boolean = false,
    val artworkFailed: Boolean = false,
    val artworkApplying: Boolean = false,
) {
    val hasTrim: Boolean get() = durationMs != null && durationMs > TrimMath.MIN_DURATION_MS

    val trimRange: TrimRange?
        get() = TrimMath.trimRange(trimStartMs, trimEndMs, durationMs)

    /** Editing tags of a writable MP3 without a trim needs no re-encode. */
    val tagOnlyEdit: Boolean
        get() = input?.kind == InputKind.MP3 && input.canEditInPlace && trimRange == null

    val effectiveDurationMs: Long?
        get() = TrimMath.effectiveDurationMs(
            trimRange,
            durationMs,
        )

    val hasOcrDetails: Boolean get() = ocrAttempted || ocrFailed
}

class ReviewViewModel(
    private val container: AppContainer,
    private val inputId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private var settings = AppSettings()
    private var embeddedTags: TagDraft? = null
    private var identifyJob: Job? = null
    private val editedFields = mutableSetOf<TagField>()

    init {
        viewModelScope.launch {
            container.processingSession.state.collect { processing ->
                if (processing !is ProcessingState.Running) {
                    _state.update { it.copy(saving = false) }
                }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val session = container.sessionRepository.find(inputId)
        if (session == null) {
            _state.update {
                it.copy(loading = false, errorRes = R.string.review_session_missing)
            }
            return
        }
        settings = container.settingsRepository.settings.first()
        val input = session.input
        embeddedTags = readEmbeddedTags(input)
        val initial = embeddedTags ?: TagDraft(title = defaultTitle(input))
        val duration = input.durationMs
        _state.value = ReviewUiState(
            loading = false,
            input = input,
            draft = initial,
            sources = embeddedTags?.let(::embeddedSources).orEmpty(),
            quality = settings.defaultQuality,
            trimStartMs = 0L,
            trimEndMs = duration ?: 0L,
            durationMs = duration,
            identifying = true,
            fingerprintAvailable = settings.fingerprintAvailable,
        )
        identifyNow()
    }

    fun updateDraft(field: TagField, transform: (TagDraft) -> TagDraft) {
        editedFields += field
        _state.update { current ->
            current.copy(
                draft = transform(current.draft),
                sources = current.sources + (field to IdentificationSource.MANUAL),
                errorRes = null,
            )
        }
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

    fun retryIdentification() {
        if (_state.value.identifying) return
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            identifyNow()
        }
    }

    fun search(query: String) {
        val term = query.trim()
        if (term.isEmpty() || _state.value.searching) return
        viewModelScope.launch {
            val currentSettings = container.settingsRepository.settings.first()
            settings = currentSettings
            if (!currentSettings.networkLookups) {
                _state.update {
                    it.copy(
                        searchOffline = true,
                        searchCompleted = false,
                        searchResults = emptyList(),
                        errorRes = null,
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    searching = true,
                    searchOffline = false,
                    searchCompleted = false,
                    errorRes = null,
                )
            }
            val results = try {
                container.metadataClients().resolver.search(MetadataQuery(term))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update {
                it.copy(searching = false, searchCompleted = true, searchResults = results)
            }
        }
    }

    fun applyMatch(match: MetadataMatch) {
        var appliedFields: Set<TagField> = emptySet()
        _state.update { current ->
            val sources = current.sources.toMutableMap()
            val applied = mutableSetOf<TagField>()

            fun text(field: TagField, value: String?, fallback: String): String = when {
                value.isNullOrBlank() -> fallback
                else -> {
                    applied += field
                    sources[field] = match.source
                    value
                }
            }

            fun optional(field: TagField, value: String?, fallback: String?): String? = when {
                value.isNullOrBlank() -> fallback
                else -> {
                    applied += field
                    sources[field] = match.source
                    value
                }
            }

            fun <T> optionalValue(field: TagField, value: T?, fallback: T?): T? = when {
                value == null -> fallback
                else -> {
                    applied += field
                    sources[field] = match.source
                    value
                }
            }

            val draft = current.draft.copy(
                title = text(TagField.TITLE, match.title, current.draft.title),
                artist = text(TagField.ARTIST, match.artist, current.draft.artist),
                album = text(TagField.ALBUM, match.album, current.draft.album),
                albumArtist = text(
                    TagField.ALBUM_ARTIST,
                    match.albumArtist,
                    current.draft.albumArtist,
                ),
                year = optional(TagField.YEAR, match.year, current.draft.year),
                genre = optional(TagField.GENRE, match.genre, current.draft.genre),
                trackNumber = optionalValue(
                    TagField.TRACK_NUMBER,
                    match.trackNumber,
                    current.draft.trackNumber,
                ),
                artwork = optionalValue(TagField.ARTWORK, match.artwork, current.draft.artwork),
            )
            appliedFields = applied
            current.copy(
                draft = draft,
                sources = sources,
                alternatives = current.alternatives - match,
                searchResults = current.searchResults - match,
                errorRes = null,
            )
        }
        editedFields += appliedFields
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
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val latest = container.settingsRepository.settings.first()
                settings = latest
                val snapshot = _state.value
                val jobDraft = snapshot.draft.copy(
                    year = snapshot.draft.year?.trim()?.takeIf { it.isNotEmpty() },
                )
                val trim = TrimMath.trimRange(
                    snapshot.trimStartMs,
                    snapshot.trimEndMs,
                    snapshot.durationMs,
                )
                val mode = ProcessingModeSelector.mode(input, trim)
                val job = ProcessingJob(
                    input = input,
                    draft = jobDraft,
                    trim = trim,
                    quality = snapshot.quality,
                    outputFolder = latest.outputFolder,
                    filenameTemplate = latest.filenameTemplate,
                    mode = mode,
                    changedFields = if (mode == ProcessingMode.EDIT_IN_PLACE) {
                        jobDraft.changedFieldsFrom(embeddedTags ?: TagDraft())
                    } else {
                        emptySet()
                    },
                )
                container.errorReporter.clear()
                container.sessionRepository.remember(job)
                container.processingSession.reset()
                SongProcessingService.start(container.appContext, job)
                onStarted(input.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(saving = false, errorRes = R.string.error_processing_failed)
                }
            }
        }
    }

    fun openArtworkPicker() {
        _state.update { it.copy(artworkPickerOpen = true, artworkFailed = false) }
    }

    fun closeArtworkPicker() {
        _state.update { it.copy(artworkPickerOpen = false) }
    }

    fun searchArtwork(query: String) {
        val term = query.trim()
        if (term.isEmpty() || _state.value.artworkSearching) return
        viewModelScope.launch {
            val latest = try {
                container.settingsRepository.settings.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                settings
            }
            settings = latest
            if (!latest.networkLookups) {
                _state.update {
                    it.copy(
                        artworkOffline = true,
                        artworkSearchCompleted = false,
                        artworkResults = emptyList(),
                        artworkFailed = false,
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    artworkSearching = true,
                    artworkSearchCompleted = false,
                    artworkOffline = false,
                    artworkFailed = false,
                )
            }
            val results = container.artworkRepository.search(term)
            _state.update {
                it.copy(
                    artworkSearching = false,
                    artworkSearchCompleted = true,
                    artworkResults = results,
                )
            }
        }
    }

    fun chooseArtwork(candidate: ArtworkCandidate) {
        if (_state.value.artworkApplying) return
        viewModelScope.launch {
            _state.update { it.copy(artworkApplying = true, artworkFailed = false) }
            val artwork = container.artworkRepository.load(candidate)
            if (artwork == null) {
                _state.update { it.copy(artworkApplying = false, artworkFailed = true) }
            } else {
                applyArtwork(artwork)
            }
        }
    }

    fun chooseGalleryArtwork(uri: Uri) {
        if (_state.value.artworkApplying) return
        viewModelScope.launch {
            _state.update { it.copy(artworkApplying = true, artworkFailed = false) }
            val artwork = container.artworkRepository.fromUri(uri)
            if (artwork == null) {
                _state.update { it.copy(artworkApplying = false, artworkFailed = true) }
            } else {
                applyArtwork(artwork)
            }
        }
    }

    fun removeArtwork() {
        updateDraft(TagField.ARTWORK) { it.copy(artwork = null) }
    }

    private fun applyArtwork(artwork: Artwork) {
        updateDraft(TagField.ARTWORK) { it.copy(artwork = artwork) }
        _state.update { it.copy(artworkPickerOpen = false, artworkApplying = false) }
    }

    private suspend fun identifyNow() {
        val input = _state.value.input ?: return
        settings = container.settingsRepository.settings.first()
        _state.update {
            it.copy(
                identifying = true,
                ocrFailed = false,
                fingerprintKeyRejected = false,
                searchOffline = !settings.networkLookups,
                fingerprintAvailable = settings.fingerprintAvailable,
            )
        }
        val result = try {
            container.identifySong(
                input = input,
                embeddedTags = embeddedTags,
                settings = settings,
                preferHebrew = settings.language.prefersHebrew(systemLanguage()),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (result == null) {
            _state.update {
                it.copy(
                    identifying = false,
                    ocrAttempted = input.kind == InputKind.VIDEO,
                    ocrFailed = input.kind == InputKind.VIDEO,
                )
            }
            return
        }
        applyIdentification(result)
    }

    private fun applyIdentification(result: SongIdentification) {
        _state.update { current ->
            val sources = current.sources.toMutableMap()
            val draft = mergeIdentified(
                current = current.draft,
                identified = result.outcome.tag,
                identifiedSources = result.outcome.sources,
                sources = sources,
            )
            current.copy(
                draft = draft,
                sources = sources,
                alternatives = result.outcome.alternatives,
                rawOcrLines = result.outcome.rawOcrLines,
                ocrAttempted = current.input?.kind == InputKind.VIDEO,
                ocrFailed = result.ocrFailed,
                fingerprintKeyRejected = result.outcome.fingerprintKeyRejected,
                identifying = false,
            )
        }
    }

    private fun mergeIdentified(
        current: TagDraft,
        identified: TagDraft,
        identifiedSources: Map<TagField, IdentificationSource>,
        sources: MutableMap<TagField, IdentificationSource>,
    ): TagDraft {
        fun text(field: TagField, currentValue: String, identifiedValue: String): String {
            if (field in editedFields || identifiedValue.isBlank()) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            return identifiedValue
        }

        fun optional(field: TagField, currentValue: String?, identifiedValue: String?): String? {
            if (field in editedFields || identifiedValue.isNullOrBlank()) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            return identifiedValue
        }

        fun <T> optionalValue(field: TagField, currentValue: T?, identifiedValue: T?): T? {
            if (field in editedFields || identifiedValue == null) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            return identifiedValue
        }

        return current.copy(
            title = text(TagField.TITLE, current.title, identified.title),
            artist = text(TagField.ARTIST, current.artist, identified.artist),
            album = text(TagField.ALBUM, current.album, identified.album),
            albumArtist = text(
                TagField.ALBUM_ARTIST,
                current.albumArtist,
                identified.albumArtist,
            ),
            year = optional(TagField.YEAR, current.year, identified.year),
            genre = optional(TagField.GENRE, current.genre, identified.genre),
            trackNumber = optionalValue(
                TagField.TRACK_NUMBER,
                current.trackNumber,
                identified.trackNumber,
            ),
            artwork = optionalValue(TagField.ARTWORK, current.artwork, identified.artwork),
        )
    }

    private fun embeddedSources(tag: TagDraft): Map<TagField, IdentificationSource> = buildMap {
        if (tag.title.isNotBlank()) put(TagField.TITLE, IdentificationSource.EMBEDDED_TAGS)
        if (tag.artist.isNotBlank()) put(TagField.ARTIST, IdentificationSource.EMBEDDED_TAGS)
        if (tag.album.isNotBlank()) put(TagField.ALBUM, IdentificationSource.EMBEDDED_TAGS)
        if (tag.albumArtist.isNotBlank()) {
            put(TagField.ALBUM_ARTIST, IdentificationSource.EMBEDDED_TAGS)
        }
        if (!tag.year.isNullOrBlank()) put(TagField.YEAR, IdentificationSource.EMBEDDED_TAGS)
        if (!tag.genre.isNullOrBlank()) put(TagField.GENRE, IdentificationSource.EMBEDDED_TAGS)
        if (tag.trackNumber != null) {
            put(TagField.TRACK_NUMBER, IdentificationSource.EMBEDDED_TAGS)
        }
        if (tag.artwork != null) put(TagField.ARTWORK, IdentificationSource.EMBEDDED_TAGS)
    }

    private fun systemLanguage(): String =
        container.appContext.resources.configuration.locales[0].language

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
