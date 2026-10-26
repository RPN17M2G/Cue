package io.github.rpn17m2g.cue.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.estimateMp3Bytes
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    inputId: String,
    onBack: () -> Unit,
    onProcessing: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel(key = inputId) {
        ReviewViewModel(context.appContainer(), inputId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.error_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.input == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(state.errorRes ?: R.string.review_session_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onBack) {
                    Text(stringResource(R.string.error_back))
                }
            }
            else -> ReviewContent(
                state = state,
                padding = padding,
                onUpdate = viewModel::updateDraft,
                onTrim = viewModel::setTrim,
                onQuality = viewModel::setQuality,
                onSave = { viewModel.save { onProcessing() } },
                onRetry = viewModel::retryIdentification,
                onSearch = viewModel::search,
                onApplyMatch = viewModel::applyMatch,
            )
        }
    }
}

@Composable
private fun ReviewContent(
    state: ReviewUiState,
    padding: PaddingValues,
    onUpdate: (TagField, (TagDraft) -> TagDraft) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onApplyMatch: (MetadataMatch) -> Unit,
) {
    val input = state.input ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaPreview(
            input = input,
            trimStartMs = state.trimStartMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
        Text(
            text = input.displayName,
            style = MaterialTheme.typography.titleLarge,
        )
        if (state.identifying) IdentificationBanner()
        TagFields(state = state, onUpdate = onUpdate)
        LookupSection(
            state = state,
            onRetry = onRetry,
            onSearch = onSearch,
            onApplyMatch = onApplyMatch,
        )
        OcrDetails(state = state)
        if (state.hasTrim) {
            TrimSection(state = state, onTrim = onTrim)
        }
        QualitySection(
            state = state,
            onQuality = onQuality,
            estimatedBytes = estimateMp3Bytes(
                durationMs = state.effectiveDurationMs ?: 0L,
                preset = state.quality,
            ),
        )
        state.errorRes?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Button(
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.review_save))
        }
    }
}

@Composable
private fun IdentificationBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.review_identifying),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TagFields(
    state: ReviewUiState,
    onUpdate: (TagField, (TagDraft) -> TagDraft) -> Unit,
) {
    val draft = state.draft
    OutlinedTextField(
        value = draft.title,
        onValueChange = { value -> onUpdate(TagField.TITLE) { it.copy(title = value) } },
        label = { Text(stringResource(R.string.review_field_title)) },
        supportingText = state.sourceFor(TagField.TITLE, draft.title.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.artist,
        onValueChange = { value -> onUpdate(TagField.ARTIST) { it.copy(artist = value) } },
        label = { Text(stringResource(R.string.review_field_artist)) },
        supportingText = state.sourceFor(TagField.ARTIST, draft.artist.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.album,
        onValueChange = { value -> onUpdate(TagField.ALBUM) { it.copy(album = value) } },
        label = { Text(stringResource(R.string.review_field_album)) },
        supportingText = state.sourceFor(TagField.ALBUM, draft.album.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.albumArtist,
        onValueChange = { value -> onUpdate(TagField.ALBUM_ARTIST) { it.copy(albumArtist = value) } },
        label = { Text(stringResource(R.string.review_field_album_artist)) },
        supportingText = state.sourceFor(TagField.ALBUM_ARTIST, draft.albumArtist.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.year.orEmpty(),
        onValueChange = { value -> onUpdate(TagField.YEAR) { it.copy(year = value) } },
        label = { Text(stringResource(R.string.review_field_year)) },
        supportingText = state.sourceFor(TagField.YEAR, !draft.year.isNullOrBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = draft.genre.orEmpty(),
        onValueChange = { value -> onUpdate(TagField.GENRE) { it.copy(genre = value) } },
        label = { Text(stringResource(R.string.review_field_genre)) },
        supportingText = state.sourceFor(TagField.GENRE, !draft.genre.isNullOrBlank()),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.trackNumber?.toString().orEmpty(),
        onValueChange = { value ->
            onUpdate(TagField.TRACK_NUMBER) {
                it.copy(trackNumber = value.filter(Char::isDigit).take(3).toIntOrNull())
            }
        },
        label = { Text(stringResource(R.string.review_field_track)) },
        supportingText = state.sourceFor(TagField.TRACK_NUMBER, draft.trackNumber != null),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private fun ReviewUiState.sourceFor(
    field: TagField,
    hasValue: Boolean,
): (@Composable () -> Unit)? {
    if (!hasValue) return null
    val source = sources[field] ?: return null
    return { SourceBadge(source) }
}

@Composable
private fun SourceBadge(source: IdentificationSource) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stringResource(source.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LookupSection(
    state: ReviewUiState,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onApplyMatch: (MetadataMatch) -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.review_lookup),
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onRetry, enabled = !state.identifying) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.review_search_online))
        }
    }
    if (state.searchOffline) {
        Text(
            text = stringResource(R.string.review_search_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.alternatives.isNotEmpty()) {
        OtherMatches(
            matches = state.alternatives,
            onApplyMatch = onApplyMatch,
        )
    }
    ManualSearch(state = state, onSearch = onSearch)
    if (state.searching) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.review_searching),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (state.searchCompleted) {
        if (state.searchResults.isEmpty()) {
            Text(
                text = stringResource(R.string.review_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.review_search_results),
                style = MaterialTheme.typography.titleMedium,
            )
            MatchList(matches = state.searchResults, onApplyMatch = onApplyMatch)
        }
    }
}

@Composable
private fun OtherMatches(
    matches: List<MetadataMatch>,
    onApplyMatch: (MetadataMatch) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(R.string.review_other_matches_count, matches.size))
    }
    if (expanded) {
        MatchList(matches = matches, onApplyMatch = onApplyMatch)
    }
}

@Composable
private fun MatchList(
    matches: List<MetadataMatch>,
    onApplyMatch: (MetadataMatch) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        matches.forEach { match ->
            MatchRow(match = match, onApply = { onApplyMatch(match) })
        }
    }
}

@Composable
private fun MatchRow(match: MetadataMatch, onApply: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = match.title,
                style = MaterialTheme.typography.titleMedium,
            )
            val subtitle = listOfNotNull(
                match.artist.takeIf { it.isNotBlank() },
                match.album?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(match.source.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ManualSearch(
    state: ReviewUiState,
    onSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.review_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        trailingIcon = {
            IconButton(
                onClick = { onSearch(query) },
                enabled = query.isNotBlank() && !state.searching,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.review_search_action),
                )
            }
        },
    )
}

@Composable
private fun OcrDetails(state: ReviewUiState) {
    if (!state.hasOcrDetails) return
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            stringResource(
                if (expanded) R.string.review_ocr_hide else R.string.review_ocr_details,
            ),
        )
    }
    if (expanded) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.rawOcrLines.isNotEmpty()) {
                    state.rawOcrLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (state.ocrFailed) {
                    Text(
                        text = stringResource(R.string.review_ocr_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.review_ocr_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimSection(
    state: ReviewUiState,
    onTrim: (Long, Long) -> Unit,
) {
    val durationMs = state.durationMs ?: return
    val maxSeconds = durationMs / 1_000f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.review_trim),
            style = MaterialTheme.typography.titleLarge,
        )
        RangeSlider(
            value = (state.trimStartMs / 1_000f)..(state.trimEndMs / 1_000f),
            onValueChange = { range ->
                onTrim((range.start * 1_000).toLong(), (range.endInclusive * 1_000).toLong())
            },
            valueRange = 0f..maxSeconds,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.review_trim_start,
                    DateUtils.formatElapsedTime(state.trimStartMs / 1_000),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(
                    R.string.review_trim_end,
                    DateUtils.formatElapsedTime(state.trimEndMs / 1_000),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun QualitySection(
    state: ReviewUiState,
    onQuality: (QualityPreset) -> Unit,
    estimatedBytes: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.review_quality),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QualityPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.quality == preset,
                    onClick = { onQuality(preset) },
                    label = { Text(stringResource(preset.labelRes())) },
                )
            }
        }
        Text(
            text = stringResource(
                R.string.review_estimated_size_value,
                android.text.format.Formatter.formatShortFileSize(LocalContext.current, estimatedBytes),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaPreview(
    input: ResolvedInput,
    trimStartMs: Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(input.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(input.path))))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    when (input.kind) {
        InputKind.VIDEO -> Surface(
            modifier = modifier,
            color = Color.Black,
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        InputKind.MP3 -> AudioPreview(
            player = player,
            trimStartMs = trimStartMs,
            modifier = modifier,
        )
    }
}

@Composable
private fun AudioPreview(
    player: ExoPlayer,
    trimStartMs: Long,
    modifier: Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(Unit) {
        player.pause()
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.seekTo(trimStartMs)
                        player.play()
                    }
                },
                modifier = Modifier.fillMaxSize(0.3f),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.review_pause else R.string.review_play,
                    ),
                )
            }
        }
    }
}

@Composable
private fun QualityPreset.labelRes(): Int = when (this) {
    QualityPreset.V0 -> R.string.quality_v0
    QualityPreset.V2 -> R.string.quality_v2
    QualityPreset.CBR_256 -> R.string.quality_cbr256
    QualityPreset.CBR_192 -> R.string.quality_cbr192
}

@Composable
private fun IdentificationSource.labelRes(): Int = when (this) {
    IdentificationSource.EMBEDDED_TAGS -> R.string.source_embedded
    IdentificationSource.OCR_LATIN -> R.string.source_ocr_latin
    IdentificationSource.OCR_HEBREW -> R.string.source_ocr_hebrew
    IdentificationSource.ITUNES -> R.string.source_itunes
    IdentificationSource.MUSICBRAINZ -> R.string.source_musicbrainz
    IdentificationSource.FINGERPRINT -> R.string.source_fingerprint
    IdentificationSource.MANUAL -> R.string.source_manual
}
