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
import io.github.rpn17m2g.cue.domain.identify.ArtworkPolicy
import io.github.rpn17m2g.cue.domain.identify.Confidence
import io.github.rpn17m2g.cue.domain.identify.ConfidenceTier
import io.github.rpn17m2g.cue.domain.identify.LookupSeed
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
import io.github.rpn17m2g.cue.processing.DeleteSourcePolicy
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
    val matched: MetadataMatch? = null,
    val flaggedFields: Set<TagField> = emptySet(),
    val deleteSource: Boolean = false,
    val searchResults: List<MetadataMatch> = emptyList(),
    val searchCompleted: Boolean = false,
    val searching: Boolean = false,
    val searchOffline: Boolean = false,
    val rawOcrLines: List<String> = emptyList(),
    val ocrAttempted: Boolean = false,
    val ocrFailed: Boolean = false,
    /** Title or artist was corrected, so "search again" can use that text. */
    val editedTitleOrArtist: Boolean = false,
    val fingerprintAvailable: Boolean = false,
    val fingerprintKeyRejected: Boolean = false,
    /** The last run actually asked AcoustID, and whether it answered. */
    val fingerprintTried: Boolean = false,
    val usedFingerprint: Boolean = false,
    /** True while the last run was the manual "use fingerprint" action. */
    val fingerprintRequested: Boolean = false,
    val artworkPickerOpen: Boolean = false,
    val artworkResults: List<ArtworkCandidate> = emptyList(),
    val artworkSearching: Boolean = false,
    val artworkSearchCompleted: Boolean = false,
    val artworkOffline: Boolean = false,
    val artworkFailed: Boolean = false,
    val artworkApplying: Boolean = false,
    val lyricsFetching: Boolean = false,
    val lyricsFailed: Boolean = false,
    val lyricsOffline: Boolean = false,
    /** Length shown by the player, when OCR read it; better for lyrics lookup. */
    val trackDurationMs: Long? = null,
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
    private var searchJob: Job? = null

    /**
     * Bumped by every identification run. A download that finishes after the
     * user asked for a fresh lookup must not paste its cover over the newer
     * result.
     */
    private var identifyGeneration = 0

    /** The URL this app applied by itself, so a retry may replace that cover. */
    private var autoArtworkUrl: String? = null

    private val editedFields = mutableSetOf<TagField>()

    /** Whether each field was already edited when its inline editor opened. */
    private val editedBeforeEditor = mutableMapOf<TagField, Boolean>()

    init {
        viewModelScope.launch {
            container.processingSession.state.collect { processing ->
                if (processing !is ProcessingState.Running) {
                    _state.update { it.copy(saving = false) }
                }
            }
        }
        // A rejected key is fixed in Settings, which sits on top of this
        // screen. Watching the settings flow lets the note clear (and the
        // lookup run again) as soon as a new key is saved, without a manual
        // refresh.
        viewModelScope.launch {
            container.settingsRepository.settings.collect { latest ->
                val keyChanged = latest.acoustIdKey != settings.acoustIdKey
                settings = latest
                if (keyChanged) {
                    _state.update { it.copy(fingerprintAvailable = latest.fingerprintAvailable) }
                    if (
                        _state.value.input?.kind == InputKind.VIDEO &&
                        _state.value.fingerprintKeyRejected &&
                        !_state.value.identifying
                    ) {
                        retryIdentification()
                    }
                }
            }
        }
        identifyJob = viewModelScope.launch { load() }
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
            // Videos are the only kind that can be deleted, and the decision
            // is ON by default: the user is usually done with the recording
            // once the song exists. Settings can turn the default off.
            deleteSource = DeleteSourcePolicy.offered(
                kind = input.kind,
                enabled = settings.deleteSourceAfterSave,
            ),
        )
        identifyNow()
    }

    fun updateDraft(field: TagField, transform: (TagDraft) -> TagDraft) {
        editedFields += field
        // A cover the user touched is theirs; automatic replacement stops here.
        if (field == TagField.ARTWORK) autoArtworkUrl = null
        _state.update { current ->
            current.copy(
                draft = transform(current.draft),
                sources = current.sources + (field to IdentificationSource.MANUAL),
                errorRes = null,
                editedTitleOrArtist = editedIdentityFields(),
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

    fun setDeleteSource(enabled: Boolean) {
        _state.update { it.copy(deleteSource = enabled) }
    }

    /**
     * Assigns one recognised line to a field. The cascade's guess is only a
     * starting point: when it picked the wrong line, this writes the chosen
     * text as a manual edit so a later identification cannot replace it.
     */
    fun useOcrLine(field: TagField, value: String) {
        val text = value.trim()
        if (text.isEmpty()) return
        updateDraft(field) { it.withField(field, text) }
        commitField(field)
    }

    /** Called when the user saves an inline editor, which settles its flag. */
    fun commitField(field: TagField) {
        editedBeforeEditor.remove(field)
        _state.update { it.copy(flaggedFields = it.flaggedFields - field) }
    }

    /**
     * Remembers whether the field had already been edited before this editor
     * opened, so cancelling the editor does not erase an earlier confirmed
     * edit (or claim one that never happened). Marking the field edited while
     * the editor is open stops a late identification result from replacing the
     * value the user is looking at.
     */
    fun beginFieldEdit(field: TagField) {
        editedBeforeEditor[field] = field in editedFields
        editedFields += field
    }

    /**
     * Cancel puts back only the value and source of the field being edited.
     * Restoring the whole draft would undo identification results that arrived
     * while the editor was open.
     */
    fun revertField(
        field: TagField,
        value: String,
        source: IdentificationSource?,
    ) {
        val wasEdited = editedBeforeEditor.remove(field) ?: (field in editedFields)
        if (wasEdited) editedFields += field else editedFields -= field
        _state.update { current ->
            val sources = current.sources.toMutableMap()
            if (source == null) sources.remove(field) else sources[field] = source
            current.copy(
                draft = current.draft.withField(field, value),
                sources = sources,
                errorRes = null,
                editedTitleOrArtist = editedIdentityFields(),
            )
        }
    }

    fun retryIdentification() {
        startIdentification(forceFingerprint = false, manualQuery = editedQuery())
    }

    /**
     * The manual audio-fingerprint action. It runs before the text lookup
     * instead of after it, so it can replace a wrong OCR guess; the text
     * lookup still runs when the fingerprint has no answer.
     */
    fun fingerprintLookup() {
        startIdentification(forceFingerprint = true, manualQuery = editedQuery())
    }

    /**
     * Fetches lyrics for the current title and artist. The automatic run after
     * identification normally fills this, but the database may have gained the
     * song since, or the user corrected the title afterwards.
     */
    fun fetchLyrics() {
        if (_state.value.lyricsFetching) return
        viewModelScope.launch {
            val current = _state.value
            val draft = current.draft
            if (draft.title.isBlank() || draft.artist.isBlank()) return@launch
            val latest = try {
                container.settingsRepository.settings.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                settings
            }
            settings = latest
            if (!latest.networkLookups) {
                _state.update { it.copy(lyricsOffline = true, lyricsFailed = false) }
                return@launch
            }
            _state.update {
                it.copy(lyricsFetching = true, lyricsFailed = false, lyricsOffline = false)
            }
            val duration = (current.trackDurationMs ?: current.durationMs)
                ?.div(1_000L)
                ?.toInt()
                ?.takeIf { it > 0 }
            val lyrics = try {
                container.metadataClients().lyrics.fetchOrNull(
                    title = draft.title,
                    artist = draft.artist,
                    album = draft.album.takeIf { it.isNotBlank() },
                    durationSeconds = duration,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            _state.update { state ->
                if (lyrics == null) {
                    state.copy(lyricsFetching = false, lyricsFailed = true)
                } else {
                    state.copy(
                        lyricsFetching = false,
                        lyricsFailed = false,
                        draft = state.draft.copy(lyrics = lyrics.text),
                        sources = state.sources + (TagField.LYRICS to IdentificationSource.LRCLIB),
                    )
                }
            }
        }
    }

    private fun startIdentification(forceFingerprint: Boolean, manualQuery: LookupSeed?) {
        if (_state.value.identifying) return
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            identifyNow(forceFingerprint = forceFingerprint, manualQuery = manualQuery)
        }
    }

    /**
     * The current title/artist when the user corrected either one. A plain
     * retry keeps using the OCR reading; a corrected field means the user
     * already knows better, so the next lookup has to use their text.
     *
     * The album is deliberately left out: the user corrected the song, not
     * its release, and sending the old album would pin it as a manual value
     * and stop the match from filling the right one.
     */
    private fun editedQuery(): LookupSeed? {
        if (!editedIdentityFields()) return null
        val draft = _state.value.draft
        val title = draft.title.trim()
        if (title.isEmpty()) return null
        return LookupSeed(
            title = title,
            artist = draft.artist.trim().takeIf { it.isNotEmpty() },
            durationMs = _state.value.durationMs,
        )
    }

    private fun editedIdentityFields(): Boolean = editedFields.any(::isIdentityField)

    private fun isIdentityField(field: TagField): Boolean =
        field == TagField.TITLE || field == TagField.ARTIST

    fun search(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        // Live search fires per keystroke; cancel the previous request so a
        // slow answer cannot overwrite the results for a newer query.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val currentSettings = container.settingsRepository.settings.first()
            settings = currentSettings
            if (!currentSettings.networkLookups) {
                _state.update {
                    it.copy(
                        searching = false,
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
        // Picking another row replaces the song. A cover this app applied for
        // the previous match would now be a picture of the wrong record, so it
        // goes with it; a cover the user chose is never touched.
        val dropAutoArtwork = match.artwork == null && autoArtworkUrl != null
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
                artwork = if (dropAutoArtwork) {
                    null
                } else {
                    optionalValue(TagField.ARTWORK, match.artwork, current.draft.artwork)
                },
                bpm = optionalValue(TagField.BPM, match.bpm, current.draft.bpm),
                isrc = optional(TagField.ISRC, match.isrc, current.draft.isrc),
                label = optional(TagField.LABEL, match.label, current.draft.label),
                replayGainDb = optionalValue(
                    TagField.REPLAY_GAIN,
                    match.replayGainDb,
                    current.draft.replayGainDb,
                ),
                recordingMbid = optional(
                    TagField.MUSICBRAINZ,
                    match.recordingMbid,
                    current.draft.recordingMbid,
                ),
                releaseMbid = optional(
                    TagField.MUSICBRAINZ,
                    match.releaseMbid,
                    current.draft.releaseMbid,
                ),
                artistMbid = optional(
                    TagField.MUSICBRAINZ,
                    match.artistMbid,
                    current.draft.artistMbid,
                ),
            )
            if (dropAutoArtwork) sources.remove(TagField.ARTWORK)
            appliedFields = applied
            // Applying a weak candidate is allowed — the user picked it — but
            // the fields it fills must still ask for a glance.
            val flagged = if (Confidence.tier(match.score) == ConfidenceTier.ACCEPTED) {
                current.flaggedFields - applied
            } else {
                current.flaggedFields + applied.filter { it in CHECKABLE_FIELDS }
            }
            current.copy(
                draft = draft,
                sources = sources,
                matched = match,
                alternatives = current.alternatives - match,
                searchResults = current.searchResults - match,
                flaggedFields = flagged,
                errorRes = null,
                editedTitleOrArtist = (editedFields + appliedFields).any(::isIdentityField),
            )
        }
        editedFields += appliedFields
        if (match.artwork != null || dropAutoArtwork) autoArtworkUrl = null
        if (match.artwork == null) downloadMatchArtwork(match)
    }

    /**
     * A match picked by hand should show its own cover. The lookup already
     * carries the URL, so this is a download; a cover the user picked stays
     * theirs, and offline mode does nothing.
     */
    private fun downloadMatchArtwork(match: MetadataMatch) {
        val url = match.artworkUrl?.takeIf { it.isNotBlank() } ?: return
        if (TagField.ARTWORK in editedFields) return
        viewModelScope.launch {
            val latest = try {
                container.settingsRepository.settings.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                settings
            }
            settings = latest
            if (!latest.networkLookups) return@launch
            val artwork = container.artworkRepository.fromUrl(url) ?: return@launch
            if (TagField.ARTWORK in editedFields) return@launch
            autoArtworkUrl = url
            _state.update { current ->
                current.copy(
                    draft = current.draft.copy(artwork = artwork),
                    sources = current.sources + (TagField.ARTWORK to match.source),
                )
            }
        }
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
                    deleteSource = DeleteSourcePolicy.offered(
                        kind = input.kind,
                        enabled = snapshot.deleteSource,
                    ),
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
        val current = _state.value
        if (current.artworkResults.isNotEmpty() || current.artworkSearching) return
        val query = listOf(current.draft.artist, current.draft.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isNotBlank()) searchArtwork(query)
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

    private suspend fun identifyNow(
        forceFingerprint: Boolean = false,
        manualQuery: LookupSeed? = null,
    ) {
        val input = _state.value.input ?: return
        val generation = ++identifyGeneration
        settings = container.settingsRepository.settings.first()
        _state.update {
            it.copy(
                identifying = true,
                ocrFailed = false,
                fingerprintKeyRejected = false,
                searchOffline = !settings.networkLookups,
                fingerprintAvailable = settings.fingerprintAvailable,
                fingerprintRequested = forceFingerprint,
                fingerprintTried = false,
                usedFingerprint = false,
            )
        }
        val result = try {
            container.identifySong(
                input = input,
                embeddedTags = embeddedTags,
                settings = settings,
                preferHebrew = settings.language.prefersHebrew(systemLanguage()),
                forceFingerprint = forceFingerprint,
                manualQuery = manualQuery,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (generation != identifyGeneration) return
        if (result == null) {
            _state.update {
                it.copy(
                    identifying = false,
                    ocrAttempted = if (manualQuery != null) {
                        it.ocrAttempted
                    } else {
                        input.kind == InputKind.VIDEO
                    },
                    ocrFailed = if (manualQuery != null) {
                        it.ocrFailed
                    } else {
                        input.kind == InputKind.VIDEO
                    },
                )
            }
            return
        }
        applyIdentification(result, preserveOcr = manualQuery != null)
        applyAutoArtwork(result, generation)
        // The refresh button stays disabled until the cover settles, so a
        // second lookup cannot race the download.
        if (generation == identifyGeneration) {
            _state.update { it.copy(identifying = false) }
        }
    }

    /**
     * Fills the cover from the best match when the match is confident. The
     * lookup already carried the provider's artwork URL, so this is a download,
     * not another search. [ArtworkPolicy] decides whether the draft is fair
     * game; a cover the user chose or removed is never overwritten, and a
     * failed download leaves the draft untouched.
     */
    private suspend fun applyAutoArtwork(result: SongIdentification, generation: Int) {
        val best = result.outcome.bestMatch ?: return
        val latest = try {
            container.settingsRepository.settings.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            settings
        }
        settings = latest
        val url = ArtworkPolicy.autoApplyUrl(
            match = best,
            hasArtwork = _state.value.draft.artwork != null,
            autoAppliedUrl = autoArtworkUrl,
            artworkEdited = TagField.ARTWORK in editedFields,
            networkEnabled = latest.networkLookups,
        ) ?: return
        val artwork = container.artworkRepository.fromUrl(url) ?: return
        if (generation != identifyGeneration) return
        // The user may have picked a cover while the download was in flight;
        // their choice wins. Both run on the main dispatcher, so this check and
        // the update cannot interleave with another edit.
        if (TagField.ARTWORK in editedFields) return
        autoArtworkUrl = url
        _state.update { current ->
            current.copy(
                draft = current.draft.copy(artwork = artwork),
                sources = current.sources + (TagField.ARTWORK to best.source),
            )
        }
    }

    private fun applyIdentification(result: SongIdentification, preserveOcr: Boolean = false) {
        _state.update { current ->
            val sources = current.sources.toMutableMap()
            // Only the fields this run actually took from the provider can be
            // blamed on it; values kept from an earlier run are not flagged.
            val applied = mutableSetOf<TagField>()
            val draft = mergeIdentified(
                current = current.draft,
                identified = result.outcome.tag,
                identifiedSources = result.outcome.sources,
                sources = sources,
                applied = applied,
            )
            val best = result.outcome.bestMatch
                ?.takeIf { Confidence.tier(it.score) != ConfidenceTier.IGNORED }
            // A mid-tier match is shown, but the fields it actually filled ask
            // for a glance. Fields the user edited stay out of the banner.
            val flagged = if (
                best != null && Confidence.tier(best.score) == ConfidenceTier.CHECK
            ) {
                applied.filterTo(mutableSetOf()) {
                    it in CHECKABLE_FIELDS && it !in editedFields
                }
            } else {
                emptySet()
            }
            current.copy(
                draft = draft,
                sources = sources,
                alternatives = result.outcome.alternatives.filter {
                    Confidence.tier(it.score) != ConfidenceTier.IGNORED
                },
                matched = best,
                flaggedFields = flagged,
                // A manual search skips OCR, so the lines and failure note
                // already on screen stay valid instead of being wiped.
                rawOcrLines = if (preserveOcr) current.rawOcrLines else result.outcome.rawOcrLines,
                ocrAttempted = if (preserveOcr) {
                    current.ocrAttempted
                } else {
                    current.input?.kind == InputKind.VIDEO
                },
                ocrFailed = if (preserveOcr) current.ocrFailed else result.ocrFailed,
                fingerprintKeyRejected = result.outcome.fingerprintKeyRejected,
                fingerprintTried = result.outcome.fingerprintTried,
                usedFingerprint = result.outcome.usedFingerprint,
                trackDurationMs = result.trackDurationMs ?: current.trackDurationMs,
            )
        }
    }

    private fun mergeIdentified(
        current: TagDraft,
        identified: TagDraft,
        identifiedSources: Map<TagField, IdentificationSource>,
        sources: MutableMap<TagField, IdentificationSource>,
        applied: MutableSet<TagField>,
    ): TagDraft {
        fun text(field: TagField, currentValue: String, identifiedValue: String): String {
            if (field in editedFields || identifiedValue.isBlank()) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            applied += field
            return identifiedValue
        }

        fun optional(field: TagField, currentValue: String?, identifiedValue: String?): String? {
            if (field in editedFields || identifiedValue.isNullOrBlank()) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            applied += field
            return identifiedValue
        }

        fun <T> optionalValue(field: TagField, currentValue: T?, identifiedValue: T?): T? {
            if (field in editedFields || identifiedValue == null) return currentValue
            identifiedSources[field]?.let { sources[field] = it }
            applied += field
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
            lyrics = optional(TagField.LYRICS, current.lyrics, identified.lyrics),
            bpm = optionalValue(TagField.BPM, current.bpm, identified.bpm),
            isrc = optional(TagField.ISRC, current.isrc, identified.isrc),
            label = optional(TagField.LABEL, current.label, identified.label),
            replayGainDb = optionalValue(
                TagField.REPLAY_GAIN,
                current.replayGainDb,
                identified.replayGainDb,
            ),
            recordingMbid = optional(
                TagField.MUSICBRAINZ,
                current.recordingMbid,
                identified.recordingMbid,
            ),
            releaseMbid = optional(
                TagField.MUSICBRAINZ,
                current.releaseMbid,
                identified.releaseMbid,
            ),
            artistMbid = optional(
                TagField.MUSICBRAINZ,
                current.artistMbid,
                identified.artistMbid,
            ),
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
        if (!tag.lyrics.isNullOrBlank()) put(TagField.LYRICS, IdentificationSource.EMBEDDED_TAGS)
        if (tag.bpm != null) put(TagField.BPM, IdentificationSource.EMBEDDED_TAGS)
        if (!tag.isrc.isNullOrBlank()) put(TagField.ISRC, IdentificationSource.EMBEDDED_TAGS)
        if (!tag.label.isNullOrBlank()) put(TagField.LABEL, IdentificationSource.EMBEDDED_TAGS)
        if (tag.replayGainDb != null) {
            put(TagField.REPLAY_GAIN, IdentificationSource.EMBEDDED_TAGS)
        }
        if (!tag.recordingMbid.isNullOrBlank() ||
            !tag.releaseMbid.isNullOrBlank() ||
            !tag.artistMbid.isNullOrBlank()
        ) {
            put(TagField.MUSICBRAINZ, IdentificationSource.EMBEDDED_TAGS)
        }
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
        val CHECKABLE_FIELDS = setOf(
            TagField.TITLE,
            TagField.ARTIST,
            TagField.ALBUM,
            TagField.YEAR,
        )
    }
}

/**
 * Writes one text field back into a draft. Shared by the inline editor and by
 * Cancel's single-field restore, so both agree on how a field is represented.
 */
internal fun TagDraft.withField(field: TagField, value: String): TagDraft = when (field) {
    TagField.TITLE -> copy(title = value)
    TagField.ARTIST -> copy(artist = value)
    TagField.ALBUM -> copy(album = value)
    TagField.ALBUM_ARTIST -> copy(albumArtist = value)
    TagField.YEAR -> copy(year = value)
    TagField.GENRE -> copy(genre = value)
    TagField.TRACK_NUMBER -> copy(trackNumber = value.takeIf { it.isNotBlank() }?.toIntOrNull())
    TagField.ARTWORK -> this
    TagField.LYRICS -> copy(lyrics = value.takeIf { it.isNotBlank() })
    TagField.BPM -> copy(bpm = value.trim().toIntOrNull()?.takeIf { it > 0 })
    TagField.ISRC -> copy(isrc = value.takeIf { it.isNotBlank() })
    TagField.LABEL -> copy(label = value.takeIf { it.isNotBlank() })
    TagField.MUSICBRAINZ -> this
    TagField.REPLAY_GAIN -> copy(replayGainDb = value.trim().toDoubleOrNull())
}
