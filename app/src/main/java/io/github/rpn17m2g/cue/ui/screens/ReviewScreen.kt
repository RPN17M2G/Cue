package io.github.rpn17m2g.cue.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.metadata.ArtworkCandidate
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.Artwork
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
        containerColor = MaterialTheme.colorScheme.background,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (!state.loading && state.input != null) {
                ReviewActionBar(
                    tagOnlyEdit = state.tagOnlyEdit,
                    saving = state.saving,
                    onSave = { viewModel.save { onProcessing() } },
                )
            }
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
                onRetry = viewModel::retryIdentification,
                onSearch = viewModel::search,
                onApplyMatch = viewModel::applyMatch,
                onOpenArtworkPicker = viewModel::openArtworkPicker,
                onCloseArtworkPicker = viewModel::closeArtworkPicker,
                onSearchArtwork = viewModel::searchArtwork,
                onChooseArtwork = viewModel::chooseArtwork,
                onGalleryArtwork = viewModel::chooseGalleryArtwork,
                onRemoveArtwork = viewModel::removeArtwork,
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
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onApplyMatch: (MetadataMatch) -> Unit,
    onOpenArtworkPicker: () -> Unit,
    onCloseArtworkPicker: () -> Unit,
    onSearchArtwork: (String) -> Unit,
    onChooseArtwork: (ArtworkCandidate) -> Unit,
    onGalleryArtwork: (android.net.Uri) -> Unit,
    onRemoveArtwork: () -> Unit,
) {
    val input = state.input ?: return
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onGalleryArtwork(uri)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        MediaPreview(
            input = input,
            trimStartMs = state.trimStartMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        Spacer(Modifier.height(12.dp))
        FileSummary(input = input, durationMs = state.durationMs)
        if (state.identifying) {
            Spacer(Modifier.height(12.dp))
            IdentificationBanner()
        }

        Spacer(Modifier.height(28.dp))
        SectionHeader(R.string.review_lookup)
        Spacer(Modifier.height(12.dp))
        TagFields(state = state, onUpdate = onUpdate)

        Spacer(Modifier.height(28.dp))
        ArtworkSection(
            artwork = state.draft.artwork,
            applying = state.artworkApplying,
            onSearchCovers = onOpenArtworkPicker,
            onPickGallery = {
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemove = onRemoveArtwork,
        )

        Spacer(Modifier.height(28.dp))
        LookupSection(
            state = state,
            onRetry = onRetry,
            onSearch = onSearch,
            onApplyMatch = onApplyMatch,
        )
        Spacer(Modifier.height(8.dp))
        OcrDetails(state = state)

        if (state.hasTrim) {
            Spacer(Modifier.height(28.dp))
            TrimSection(state = state, onTrim = onTrim)
        }
        if (!state.tagOnlyEdit) {
            Spacer(Modifier.height(28.dp))
            QualitySection(
                state = state,
                onQuality = onQuality,
                estimatedBytes = estimateMp3Bytes(
                    durationMs = state.effectiveDurationMs ?: 0L,
                    preset = state.quality,
                ),
            )
        }
        state.errorRes?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (state.artworkPickerOpen) {
        ArtworkPickerDialog(
            state = state,
            onDismiss = onCloseArtworkPicker,
            onSearch = onSearchArtwork,
            onChoose = onChooseArtwork,
        )
    }
}

@Composable
private fun FileSummary(
    input: ResolvedInput,
    durationMs: Long?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (input.kind == InputKind.VIDEO) {
                Icons.Default.Movie
            } else {
                Icons.Default.MusicNote
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = input.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (durationMs != null && durationMs > 0L) {
            Text(
                text = DateUtils.formatElapsedTime(durationMs / 1_000),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small, quiet label that separates the form's sections. */
@Composable
private fun SectionHeader(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.6.sp,
    )
}

/** Sticky primary action, so Process is always reachable. */
@Composable
private fun ReviewActionBar(
    tagOnlyEdit: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = stringResource(
                            if (tagOnlyEdit) R.string.review_save_tags else R.string.review_save,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TagField(
            labelRes = R.string.review_field_title,
            value = draft.title,
            source = state.sourceFor(TagField.TITLE, draft.title.isNotBlank()),
            onValueChange = { value -> onUpdate(TagField.TITLE) { it.copy(title = value) } },
        )
        TagField(
            labelRes = R.string.review_field_artist,
            value = draft.artist,
            source = state.sourceFor(TagField.ARTIST, draft.artist.isNotBlank()),
            onValueChange = { value -> onUpdate(TagField.ARTIST) { it.copy(artist = value) } },
        )
        TagField(
            labelRes = R.string.review_field_album,
            value = draft.album,
            source = state.sourceFor(TagField.ALBUM, draft.album.isNotBlank()),
            onValueChange = { value -> onUpdate(TagField.ALBUM) { it.copy(album = value) } },
        )
        TagField(
            labelRes = R.string.review_field_album_artist,
            value = draft.albumArtist,
            source = state.sourceFor(TagField.ALBUM_ARTIST, draft.albumArtist.isNotBlank()),
            onValueChange = { value ->
                onUpdate(TagField.ALBUM_ARTIST) { it.copy(albumArtist = value) }
            },
        )
        TagField(
            labelRes = R.string.review_field_year,
            value = draft.year.orEmpty(),
            source = state.sourceFor(TagField.YEAR, !draft.year.isNullOrBlank()),
            onValueChange = { value -> onUpdate(TagField.YEAR) { it.copy(year = value) } },
            keyboardType = KeyboardType.Number,
        )
        TagField(
            labelRes = R.string.review_field_genre,
            value = draft.genre.orEmpty(),
            source = state.sourceFor(TagField.GENRE, !draft.genre.isNullOrBlank()),
            onValueChange = { value -> onUpdate(TagField.GENRE) { it.copy(genre = value) } },
        )
        TagField(
            labelRes = R.string.review_field_track,
            value = draft.trackNumber?.toString().orEmpty(),
            source = state.sourceFor(TagField.TRACK_NUMBER, draft.trackNumber != null),
            onValueChange = { value ->
                onUpdate(TagField.TRACK_NUMBER) {
                    it.copy(trackNumber = value.filter(Char::isDigit).take(3).toIntOrNull())
                }
            },
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
private fun TagField(
    labelRes: Int,
    value: String,
    source: IdentificationSource?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        supportingText = if (source != null) {
            { SourceLabel(source) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = cueOutlinedFieldColors(),
    )
}

@Composable
private fun cueOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun ArtworkSection(
    artwork: Artwork?,
    applying: Boolean,
    onSearchCovers: () -> Unit,
    onPickGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(R.string.review_artwork)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (artwork != null) {
                ArtworkThumbnail(artwork = artwork, modifier = Modifier.size(96.dp))
            } else {
                Surface(
                    modifier = Modifier.size(96.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.review_artwork_none),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onSearchCovers, enabled = !applying) {
                    Text(stringResource(R.string.review_artwork_search))
                }
                TextButton(onClick = onPickGallery, enabled = !applying) {
                    Text(
                        text = stringResource(R.string.review_artwork_gallery),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (artwork != null) {
                    TextButton(onClick = onRemove, enabled = !applying) {
                        Text(
                            text = stringResource(R.string.review_artwork_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (applying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ArtworkThumbnail(
    artwork: Artwork,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(artwork) { decodeArtwork(artwork.bytes) }
    if (bitmap == null) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {}
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.review_field_artwork),
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
        )
    }
}

@Composable
private fun ArtworkPickerDialog(
    state: ReviewUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onChoose: (ArtworkCandidate) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_artwork_search_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.review_artwork_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    trailingIcon = {
                        IconButton(
                            onClick = { onSearch(query) },
                            enabled = query.isNotBlank() && !state.artworkSearching,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.review_search_action),
                            )
                        }
                    },
                )
                when {
                    state.artworkOffline -> Text(
                        text = stringResource(R.string.review_artwork_offline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.artworkSearching -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.review_searching),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.artworkFailed -> Text(
                        text = stringResource(R.string.review_artwork_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.artworkSearchCompleted && state.artworkResults.isEmpty() -> Text(
                        text = stringResource(R.string.review_artwork_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.artworkResults.isNotEmpty() -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.artworkResults, key = { it.artworkUrl600 }) { candidate ->
                            CoverCell(candidate = candidate, onClick = { onChoose(candidate) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.review_artwork_close))
            }
        },
    )
}

@Composable
private fun CoverCell(
    candidate: ArtworkCandidate,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer().artworkRepository }
    var preview by remember(candidate.artworkUrl600) {
        mutableStateOf<PreviewState>(PreviewState.Loading)
    }
    LaunchedEffect(candidate.artworkUrl600) {
        val bitmap = repository.preview(candidate.artworkUrl600)
        preview = if (bitmap == null) PreviewState.Failed else PreviewState.Loaded(bitmap)
    }
    val fallbackDescription = stringResource(R.string.review_artwork_choose)
    val description = listOfNotNull(candidate.album, candidate.artist)
        .joinToString(" · ")
        .ifBlank { fallbackDescription }
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        when (val current = preview) {
            PreviewState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            PreviewState.Failed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.review_artwork_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is PreviewState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data object Failed : PreviewState
    data class Loaded(val bitmap: Bitmap) : PreviewState
}

private fun decodeArtwork(bytes: ByteArray): Bitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun ReviewUiState.sourceFor(
    field: TagField,
    hasValue: Boolean,
): IdentificationSource? {
    if (!hasValue) return null
    return sources[field]
}

/** Where a field's value came from, as a quiet dot and label. */
@Composable
private fun SourceLabel(source: IdentificationSource) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = stringResource(source.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionHeader(R.string.review_section_online)
        TextButton(onClick = onRetry, enabled = !state.identifying) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.review_refresh),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (state.searchOffline) {
        Text(
            text = stringResource(R.string.review_search_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.fingerprintKeyRejected) {
        Text(
            text = stringResource(R.string.review_fingerprint_key_rejected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (
        state.input?.kind == InputKind.VIDEO &&
        !state.fingerprintAvailable &&
        !state.identifying &&
        state.rawOcrLines.isEmpty()
    ) {
        Text(
            text = stringResource(R.string.review_fingerprint_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        onClick = onApply,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = match.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(match.source.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.review_apply),
                style = MaterialTheme.typography.labelLarge,
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
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        onSearch(query)
        keyboard?.hide()
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.review_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        colors = cueOutlinedFieldColors(),
        trailingIcon = {
            IconButton(
                onClick = submit,
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
    TextButton(
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (expanded) R.string.review_ocr_hide else R.string.review_ocr_details,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimSection(
    state: ReviewUiState,
    onTrim: (Long, Long) -> Unit,
) {
    val durationMs = state.durationMs ?: return
    val maxSeconds = durationMs / 1_000f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(R.string.review_trim)
        RangeSlider(
            value = (state.trimStartMs / 1_000f)..(state.trimEndMs / 1_000f),
            onValueChange = { range ->
                onTrim((range.start * 1_000).toLong(), (range.endInclusive * 1_000).toLong())
            },
            valueRange = 0f..maxSeconds,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            track = { sliderState -> TrimTrack(sliderState) },
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.review_trim_end,
                    DateUtils.formatElapsedTime(state.trimEndMs / 1_000),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A slim two-handle track; the stock slider's 16dp bar reads as a progress bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimTrack(state: RangeSliderState) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val range = state.valueRange
    val span = range.endInclusive - range.start
    val startFraction = if (span > 0f) {
        ((state.activeRangeStart - range.start) / span).coerceIn(0f, 1f)
    } else {
        0f
    }
    val endFraction = if (span > 0f) {
        ((state.activeRangeEnd - range.start) / span).coerceIn(0f, 1f)
    } else {
        0f
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRIM_TRACK_HEIGHT),
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun position(fraction: Float): Float =
            if (rtl) size.width * (1f - fraction) else size.width * fraction

        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = inactiveColor, cornerRadius = radius, size = size)
        val from = position(startFraction)
        val to = position(endFraction)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(minOf(from, to), 0f),
            size = Size(kotlin.math.abs(to - from), size.height),
            cornerRadius = radius,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualitySection(
    state: ReviewUiState,
    onQuality: (QualityPreset) -> Unit,
    estimatedBytes: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(R.string.review_quality)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            style = MaterialTheme.typography.bodyMedium,
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
        InputKind.VIDEO -> VideoPreview(
            player = player,
            trimStartMs = trimStartMs,
            modifier = modifier,
        )
        InputKind.MP3 -> AudioPreview(
            player = player,
            trimStartMs = trimStartMs,
            modifier = modifier,
        )
    }
}

/**
 * The default Media3 controller fills the card with transport chrome, so the
 * preview uses none of it and draws its own single play button over the frame.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(
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
    LaunchedEffect(player) {
        player.pause()
    }
    val toggle = {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.currentPosition < trimStartMs) player.seekTo(trimStartMs)
            player.play()
        }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = toggle),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (!isPlaying) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.review_play),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
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
            Surface(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.seekTo(trimStartMs)
                        player.play()
                    }
                },
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (isPlaying) R.string.review_pause else R.string.review_play,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
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

private val TRIM_TRACK_HEIGHT = 6.dp
