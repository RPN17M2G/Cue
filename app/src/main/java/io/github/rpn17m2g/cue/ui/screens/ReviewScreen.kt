package io.github.rpn17m2g.cue.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.estimateMp3Bytes
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel
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
            )
        }
    }
}

@Composable
private fun ReviewContent(
    state: ReviewUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onUpdate: ((io.github.rpn17m2g.cue.domain.model.TagDraft) -> io.github.rpn17m2g.cue.domain.model.TagDraft) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onSave: () -> Unit,
) {
    val input = state.input ?: return
    val context = LocalContext.current
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
        TagFields(state = state, onUpdate = onUpdate)
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
private fun TagFields(
    state: ReviewUiState,
    onUpdate: ((io.github.rpn17m2g.cue.domain.model.TagDraft) -> io.github.rpn17m2g.cue.domain.model.TagDraft) -> Unit,
) {
    val draft = state.draft
    OutlinedTextField(
        value = draft.title,
        onValueChange = { value -> onUpdate { it.copy(title = value) } },
        label = { Text(stringResource(R.string.review_field_title)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.artist,
        onValueChange = { value -> onUpdate { it.copy(artist = value) } },
        label = { Text(stringResource(R.string.review_field_artist)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.album,
        onValueChange = { value -> onUpdate { it.copy(album = value) } },
        label = { Text(stringResource(R.string.review_field_album)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.albumArtist,
        onValueChange = { value -> onUpdate { it.copy(albumArtist = value) } },
        label = { Text(stringResource(R.string.review_field_album_artist)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.year.orEmpty(),
        onValueChange = { value -> onUpdate { it.copy(year = value) } },
        label = { Text(stringResource(R.string.review_field_year)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        value = draft.genre.orEmpty(),
        onValueChange = { value -> onUpdate { it.copy(genre = value) } },
        label = { Text(stringResource(R.string.review_field_genre)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.trackNumber?.toString().orEmpty(),
        onValueChange = { value ->
            onUpdate {
                it.copy(trackNumber = value.filter(Char::isDigit).take(3).toIntOrNull())
            }
        },
        label = { Text(stringResource(R.string.review_field_track)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
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
