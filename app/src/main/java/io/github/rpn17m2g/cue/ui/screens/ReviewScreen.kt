package io.github.rpn17m2g.cue.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import io.github.rpn17m2g.cue.domain.identify.Confidence
import io.github.rpn17m2g.cue.domain.identify.ConfidenceTier
import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TrimClock
import io.github.rpn17m2g.cue.domain.model.TrimMath
import io.github.rpn17m2g.cue.domain.model.estimateMp3Bytes
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.theme.CueMark
import io.github.rpn17m2g.cue.ui.theme.LocalCueTokens
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    inputId: String,
    onBack: () -> Unit,
    onProcessing: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel(key = inputId) {
        ReviewViewModel(context.appContainer(), inputId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The inline editor lives here rather than inside the scrolling content so
    // Create can settle a field that is still open. Saveable state keeps a
    // half-typed value alive across rotation.
    var editingField by rememberSaveable { mutableStateOf<TagField?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editError by rememberSaveable { mutableStateOf(false) }
    var capturedValue by rememberSaveable { mutableStateOf("") }
    var capturedSource by rememberSaveable { mutableStateOf<IdentificationSource?>(null) }

    /** Returns false when validation rejects the value, leaving the editor open. */
    fun commitField(): Boolean {
        val field = editingField ?: return true
        val year = editText.trim()
        if (field == TagField.YEAR && year.isNotEmpty() && !YEAR_PATTERN.matches(year)) {
            editError = true
            return false
        }
        // The hero fields already preview live; writing here settles every
        // field on Save, so a half-typed value never reaches the tag.
        viewModel.updateDraft(field) { it.withField(field, editText.sanitizedFor(field)) }
        viewModel.commitField(field)
        editingField = null
        editError = false
        return true
    }

    fun openField(field: TagField) {
        if (editingField == field) return
        // Switching rows saves the open editor through the same path as Save,
        // so an invalid year keeps the editor (and its error) open.
        if (editingField != null && !commitField()) return
        viewModel.beginFieldEdit(field)
        capturedValue = state.valueFor(field)
        capturedSource = state.sources[field]
        editingField = field
        editText = capturedValue
        editError = false
    }

    fun cancelField() {
        val field = editingField ?: return
        viewModel.revertField(field, capturedValue, capturedSource)
        editingField = null
        editError = false
    }

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
            // While a field is being edited the keyboard owns the bottom of the
            // screen: hiding the sticky Create bar gives Save/Cancel the room
            // they need and stops the bar from covering the open editor.
            if (!state.loading && state.input != null && editingField == null) {
                CreateBar(
                    state = state,
                    onSave = {
                        // A field that is still open counts: settle it before
                        // the job reads the draft, and keep the editor open on
                        // invalid input.
                        if (commitField()) viewModel.save { onProcessing() }
                    },
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
                onTrim = viewModel::setTrim,
                onQuality = viewModel::setQuality,
                onDeleteSource = viewModel::setDeleteSource,
                editingField = editingField,
                editText = editText,
                editError = editError,
                onOpenField = ::openField,
                onFieldTextChange = { field, value ->
                    val sanitized = value.sanitizedFor(field)
                    editText = sanitized
                    editError = false
                    // Title and artist drive the cover preview, so they update
                    // live; the other fields wait for Save.
                    if (field == TagField.TITLE || field == TagField.ARTIST) {
                        viewModel.updateDraft(field) { it.withField(field, sanitized) }
                    }
                },
                onSaveField = { commitField() },
                onCancelField = ::cancelField,
                onRetry = viewModel::retryIdentification,
                onFingerprint = viewModel::fingerprintLookup,
                onOpenSettings = onOpenSettings,
                onSearch = viewModel::search,
                onApplyMatch = viewModel::applyMatch,
                onOpenArtworkPicker = viewModel::openArtworkPicker,
                onCloseArtworkPicker = viewModel::closeArtworkPicker,
                onSearchArtwork = viewModel::searchArtwork,
                onChooseArtwork = viewModel::chooseArtwork,
                onGalleryArtwork = viewModel::chooseGalleryArtwork,
                onRemoveArtwork = viewModel::removeArtwork,
                onUseOcrLine = viewModel::useOcrLine,
            )
        }
    }
}

@Composable
private fun ReviewContent(
    state: ReviewUiState,
    padding: PaddingValues,
    onTrim: (Long, Long) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onDeleteSource: (Boolean) -> Unit,
    editingField: TagField?,
    editText: String,
    editError: Boolean,
    onOpenField: (TagField) -> Unit,
    onFieldTextChange: (TagField, String) -> Unit,
    onSaveField: () -> Unit,
    onCancelField: () -> Unit,
    onRetry: () -> Unit,
    onFingerprint: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit,
    onApplyMatch: (MetadataMatch) -> Unit,
    onOpenArtworkPicker: () -> Unit,
    onCloseArtworkPicker: () -> Unit,
    onSearchArtwork: (String) -> Unit,
    onChooseArtwork: (ArtworkCandidate) -> Unit,
    onGalleryArtwork: (Uri) -> Unit,
    onRemoveArtwork: () -> Unit,
    onUseOcrLine: (TagField, String) -> Unit,
) {
    val input = state.input ?: return
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onGalleryArtwork(uri)
    }

    var showMoreFields by rememberSaveable { mutableStateOf(false) }
    var clipExpanded by rememberSaveable { mutableStateOf(false) }
    var matchesSheet by rememberSaveable { mutableStateOf(false) }
    var searchSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 0.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverHero(
            artwork = state.draft.artwork,
            title = state.draft.title.ifBlank { stringResource(R.string.review_untitled) },
            artist = state.draft.artist,
            durationMs = state.effectiveDurationMs,
            source = state.matched?.source ?: state.sources[TagField.TITLE],
            score = state.matched?.score,
            usedFingerprint = state.usedFingerprint,
            onChangeCover = onOpenArtworkPicker,
        )

        CheckBanner(
            flaggedField = state.flaggedFields.firstOrNull(),
            onFix = { state.flaggedFields.firstOrNull()?.let(onOpenField) },
        )

        IdentificationCard(
            state = state,
            onRetry = onRetry,
            onFingerprint = onFingerprint,
            onOpenSettings = onOpenSettings,
            onOpenMatches = { matchesSheet = true },
            onOpenSearch = { searchSheet = true },
        )

        SectionHeader(stringResource(R.string.review_song_details))

        FieldsCard(
            state = state,
            editingField = editingField,
            editText = editText,
            editError = editError,
            showMoreFields = showMoreFields,
            onToggleMore = { showMoreFields = !showMoreFields },
            onOpen = onOpenField,
            onTextChange = { value ->
                editingField?.let { onFieldTextChange(it, value) }
            },
            onSave = onSaveField,
            onCancel = onCancelField,
        )

        SectionHeader(stringResource(R.string.review_clip_quality))

        ClipQualityCard(
            state = state,
            input = input,
            expanded = clipExpanded,
            onToggle = { clipExpanded = !clipExpanded },
            onTrim = onTrim,
            onQuality = onQuality,
        )

        if (input.kind == InputKind.VIDEO) {
            SectionHeader(stringResource(R.string.review_section_after))
            DeleteSourceCard(
                checked = state.deleteSource,
                onCheckedChange = onDeleteSource,
            )
        }

        if (state.hasOcrDetails) {
            OcrDetails(state = state, onUseLine = onUseOcrLine)
        }

        Spacer(Modifier.height(2.dp))
    }

    if (state.artworkPickerOpen) {
        CoverSheet(
            state = state,
            onDismiss = onCloseArtworkPicker,
            onSearch = onSearchArtwork,
            onChoose = onChooseArtwork,
            onPickDevice = {
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemove = onRemoveArtwork,
        )
    }
    if (matchesSheet) {
        MatchesSheet(
            matches = state.alternatives,
            onDismiss = { matchesSheet = false },
            onChoose = { match ->
                onApplyMatch(match)
                matchesSheet = false
            },
        )
    }
    if (searchSheet) {
        SearchSheet(
            state = state,
            initialQuery = listOf(state.draft.title, state.draft.artist)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            onDismiss = { searchSheet = false },
            onSearch = onSearch,
            onChoose = { match ->
                onApplyMatch(match)
                searchSheet = false
            },
        )
    }
}

// ---------------------------------------------------------------- hero

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverHero(
    artwork: Artwork?,
    title: String,
    artist: String,
    durationMs: Long?,
    source: IdentificationSource?,
    score: Double?,
    usedFingerprint: Boolean,
    onChangeCover: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    val bitmap = remember(artwork) { artwork?.let { decodeArtwork(it.bytes) } }
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A long title at a large accessibility font must not push the
            // metadata chips out of the hero.
            .heightIn(min = 232.dp * fontScale.coerceIn(1f, 1.5f))
            .aspectRatio(1.5f)
            .shadow(
                elevation = tokens.shadowElevation * 1.5f,
                shape = tokens.cardShape,
                ambientColor = tokens.glow.copy(alpha = 0.45f),
                spotColor = tokens.glow.copy(alpha = 0.55f),
            )
            .clip(tokens.cardShape),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.review_field_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.heroBrush),
                contentAlignment = Alignment.Center,
            ) {
                CueMark(
                    modifier = Modifier.size(96.dp),
                    tint = tokens.heroInk.copy(alpha = 0.14f),
                    contentColor = tokens.heroInk.copy(alpha = 0.30f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Bottom-heavy and monotonic: the title sits low, so the
                    // scrim must never thin out under it.
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.04f),
                        0.38f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f),
                        0.70f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.60f),
                        1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.90f),
                    ),
                ),
        )
        Surface(
            onClick = onChangeCover,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = if (artwork == null) {
                        Icons.Default.AddPhotoAlternate
                    } else {
                        Icons.Default.Edit
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(
                        if (artwork == null) {
                            R.string.review_add_cover
                        } else {
                            R.string.review_change_cover
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.heroInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.heroInk.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                durationMs?.takeIf { it > 0 }?.let { duration ->
                    HeroChip(text = TrimClock.format(duration))
                }
                source?.let { HeroChip(text = stringResource(it.labelRes())) }
                score?.let { HeroScoreChip(score = it, usedFingerprint = usedFingerprint) }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f),
        contentColor = LocalCueTokens.current.heroInk,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HeroScoreChip(score: Double, usedFingerprint: Boolean) {
    val tokens = LocalCueTokens.current
    val accent = when (Confidence.tier(score)) {
        ConfidenceTier.ACCEPTED -> tokens.success
        ConfidenceTier.CHECK -> tokens.caution
        ConfidenceTier.IGNORED -> tokens.heroInk.copy(alpha = 0.7f)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f),
        contentColor = tokens.heroInk,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = if (usedFingerprint) {
                    stringResource(R.string.review_fingerprint_short, (score * 100).roundToInt())
                } else {
                    stringResource(R.string.review_score_percent, (score * 100).roundToInt())
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ---------------------------------------------------------------- check banner

@Composable
private fun CheckBanner(
    flaggedField: TagField?,
    onFix: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    if (flaggedField == null) return
    Surface(
        shape = tokens.cardShape,
        color = tokens.caution.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, tokens.caution.copy(alpha = 0.35f)),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = tokens.caution,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    R.string.review_check_banner,
                    stringResource(flaggedField.labelRes()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFix) {
                Text(
                    text = stringResource(R.string.review_fix_it),
                    color = tokens.caution,
                )
            }
        }
    }
}

/** The same accent-dot divider the home timeline uses, so the screens rhyme. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (rtl) 0.em else 0.14.em,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

/** One card treatment for the whole screen, so every section has equal weight. */
@Composable
private fun ReviewCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalCueTokens.current
    Surface(
        shape = tokens.cardShape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = tokens.shadowElevation / 2,
                shape = tokens.cardShape,
                ambientColor = tokens.glow.copy(alpha = 0.16f),
                spotColor = tokens.glow.copy(alpha = 0.20f),
            ),
    ) {
        Column(content = content)
    }
}

// ---------------------------------------------------------------- fields

@Composable
private fun FieldsCard(
    state: ReviewUiState,
    editingField: TagField?,
    editText: String,
    editError: Boolean,
    showMoreFields: Boolean,
    onToggleMore: () -> Unit,
    onOpen: (TagField) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    ReviewCard {
        Column {
            PRIMARY_FIELDS.forEachIndexed { index, field ->
                FieldRow(
                    field = field,
                    state = state,
                    editing = editingField == field,
                    editText = editText,
                    editError = editError,
                    onOpen = { onOpen(field) },
                    onTextChange = onTextChange,
                    onSave = onSave,
                    onCancel = onCancel,
                )
                if (index < PRIMARY_FIELDS.lastIndex) {
                    FieldDivider()
                }
            }
            FieldDivider()
            TextButton(
                onClick = onToggleMore,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (showMoreFields) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (showMoreFields) R.string.review_fewer_details
                        else R.string.review_more_details,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showMoreFields) {
                EXTRA_FIELDS.forEachIndexed { index, field ->
                    FieldDivider()
                    FieldRow(
                        field = field,
                        state = state,
                        editing = editingField == field,
                        editText = editText,
                        editError = editError,
                        onOpen = { onOpen(field) },
                        onTextChange = onTextChange,
                        onSave = onSave,
                        onCancel = onCancel,
                    )
                    if (index == EXTRA_FIELDS.lastIndex) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldIcon(field: TagField) {
    val tokens = LocalCueTokens.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(tokens.thumbnailShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = field.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun TagField.icon(): ImageVector = when (this) {
    TagField.TITLE -> Icons.Default.Title
    TagField.ARTIST -> Icons.Default.Person
    TagField.ALBUM -> Icons.Default.Album
    TagField.ALBUM_ARTIST -> Icons.Default.Group
    TagField.YEAR -> Icons.Default.CalendarMonth
    TagField.GENRE -> Icons.Default.Category
    TagField.TRACK_NUMBER -> Icons.Default.Numbers
    TagField.ARTWORK -> Icons.Default.ImageIcon
}

@Composable
private fun FieldRow(
    field: TagField,
    state: ReviewUiState,
    editing: Boolean,
    editText: String,
    editError: Boolean,
    onOpen: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    val value = state.valueFor(field)
    val flagged = field in state.flaggedFields
    val source = state.sources[field]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !editing, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldIcon(field)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(field.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (flagged) {
                        Surface(
                            shape = CircleShape,
                            color = tokens.caution.copy(alpha = 0.16f),
                            contentColor = tokens.caution,
                        ) {
                            Text(
                                text = stringResource(R.string.review_check_chip),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = value.ifBlank { stringResource(R.string.review_add_value) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (source != null && value.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    SourceLabel(source)
                }
            }
            if (!editing) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.review_field_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(tween(FIELD_ANIM_MILLIS)) + expandVertically(tween(FIELD_ANIM_MILLIS)),
            exit = fadeOut(tween(FIELD_ANIM_MILLIS)) + shrinkVertically(tween(FIELD_ANIM_MILLIS)),
        ) {
            FieldEditor(
                field = field,
                text = editText,
                error = editError,
                onTextChange = onTextChange,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldEditor(
    field: TagField,
    text: String,
    error: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Selecting the whole value makes replacing it one keystroke, as the plan
    // asks. The selection lives in TextFieldValue; the parent still owns the
    // plain string.
    var value by remember {
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    LaunchedEffect(text) {
        if (value.text != text) value = value.copy(text = text)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Wait for the IME to resize the window, then lift the editor above
        // the sticky Create bar so Save and Cancel stay reachable.
        delay(EDITOR_BRING_INTO_VIEW_MILLIS)
        bringIntoView.bringIntoView()
    }
    // A long title grows the field while the user types. Re-run the request
    // after the growth settles so the actions cannot slip under the keyboard.
    LaunchedEffect(text) {
        delay(EDITOR_TEXT_BRING_INTO_VIEW_MILLIS)
        bringIntoView.bringIntoView()
    }
    LaunchedEffect(error) {
        if (error) {
            // The validation line grows the editor; keep it visible too.
            delay(EDITOR_BRING_INTO_VIEW_MILLIS)
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .bringIntoViewRequester(bringIntoView)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onCancel()
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        keyboard?.hide()
                        onSave()
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { candidate ->
                val sanitized = candidate.text.sanitizedFor(field)
                value = candidate.copy(text = sanitized)
                onTextChange(sanitized)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            // Long titles and artist names must wrap instead of scrolling
            // sideways inside a box squeezed by the Save/Cancel buttons. The
            // actions moved below the field so the full width is editable.
            singleLine = field == TagField.YEAR || field == TagField.TRACK_NUMBER,
            minLines = 1,
            maxLines = 4,
            isError = error,
            shape = LocalCueTokens.current.fieldShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = field.keyboardType(),
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    onSave()
                },
            ),
            colors = cueOutlinedFieldColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.review_field_cancel))
            }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.review_field_save))
            }
        }
        if (error) {
            Text(
                text = stringResource(R.string.review_year_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

// ---------------------------------------------------------------- match card

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentificationCard(
    state: ReviewUiState,
    onRetry: () -> Unit,
    onFingerprint: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    val match = state.matched
    val tier = match?.let { Confidence.tier(it.score) }
    val accent = when (tier) {
        ConfidenceTier.ACCEPTED -> tokens.success
        ConfidenceTier.CHECK -> tokens.caution
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isVideo = state.input?.kind == InputKind.VIDEO
    val fingerprintRunning = state.identifying && state.fingerprintRequested

    ReviewCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.review_section_identification),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        fingerprintRunning -> stringResource(R.string.review_fingerprint_running)
                        state.identifying -> stringResource(R.string.review_identifying_short)
                        match != null -> stringResource(
                            R.string.review_identified_how,
                            stringResource(match.source.labelRes()),
                        )
                        else -> stringResource(R.string.review_no_match_body)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(tier = tier, identifying = state.identifying)
        }
        if (state.identifying) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
                color = accent,
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        if (match != null) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = match.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.usedFingerprint) {
                        MiniBadge(
                            text = stringResource(R.string.review_badge_fingerprint),
                            color = tokens.success,
                        )
                    }
                }
                val subtitle = listOfNotNull(
                    match.artist.takeIf { it.isNotBlank() },
                    match.album?.takeIf { it.isNotBlank() },
                    match.year?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                ConfidencePill(
                    text = stringResource(
                        R.string.review_match_line,
                        (match.score * 100).roundToInt(),
                        stringResource(match.source.labelRes()),
                    ),
                    score = match.score,
                )
            }
        } else if (!state.identifying) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_no_match),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.review_no_match_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.usedFingerprint) {
            MatchNote(
                text = stringResource(R.string.review_fingerprint_matched),
                color = tokens.success,
            )
        }
        if (state.searchOffline) {
            MatchNote(
                text = stringResource(R.string.review_search_offline),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.fingerprintKeyRejected) {
            MatchNote(
                text = stringResource(R.string.review_fingerprint_key_rejected),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_open_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (
            isVideo &&
            !state.fingerprintAvailable &&
            !state.identifying &&
            state.rawOcrLines.isEmpty()
        ) {
            MatchNote(
                text = stringResource(R.string.review_fingerprint_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (
            state.fingerprintRequested &&
            state.fingerprintTried &&
            !state.usedFingerprint &&
            !state.fingerprintKeyRejected
        ) {
            MatchNote(
                text = stringResource(R.string.review_fingerprint_no_match),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // After a correction, the retry action searches with the text on
        // screen instead of re-reading the frames.
        val searchAgain = state.editedTitleOrArtist && state.draft.title.isNotBlank()
        FlowRow(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isVideo) {
                ActionChip(
                    label = stringResource(
                        if (searchAgain) R.string.review_search_again else R.string.review_rerun_ocr,
                    ),
                    icon = if (searchAgain) Icons.Default.Search else Icons.Default.Refresh,
                    enabled = !state.identifying,
                    onClick = onRetry,
                )
                if (state.fingerprintAvailable) {
                    ActionChip(
                        label = stringResource(
                            if (fingerprintRunning) {
                                R.string.review_fingerprint_running_action
                            } else {
                                R.string.review_use_fingerprint
                            },
                        ),
                        icon = Icons.Default.GraphicEq,
                        enabled = !state.identifying,
                        prominent = true,
                        onClick = onFingerprint,
                    )
                }
            } else {
                ActionChip(
                    label = stringResource(
                        if (searchAgain) R.string.review_search_again else R.string.review_look_up_again,
                    ),
                    icon = if (searchAgain) Icons.Default.Search else Icons.Default.Refresh,
                    enabled = !state.identifying,
                    onClick = onRetry,
                )
            }
            if (state.alternatives.isNotEmpty()) {
                ActionChip(
                    label = stringResource(
                        R.string.review_other_matches_count,
                        state.alternatives.size,
                    ),
                    icon = Icons.Default.FormatListBulleted,
                    onClick = onOpenMatches,
                )
            }
            ActionChip(
                label = stringResource(R.string.review_search_manually),
                icon = Icons.Default.Search,
                onClick = onOpenSearch,
            )
        }
    }
}

@Composable
private fun StatusPill(tier: ConfidenceTier?, identifying: Boolean) {
    val tokens = LocalCueTokens.current
    val text: String
    val color: Color
    when {
        identifying -> {
            text = stringResource(R.string.review_status_working)
            color = MaterialTheme.colorScheme.primary
        }
        tier == ConfidenceTier.ACCEPTED -> {
            text = stringResource(R.string.review_status_matched)
            color = tokens.success
        }
        tier == ConfidenceTier.CHECK -> {
            text = stringResource(R.string.review_check_chip)
            color = tokens.caution
        }
        else -> {
            text = stringResource(R.string.review_status_no_match)
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    val tokens = LocalCueTokens.current
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        prominent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        prominent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = tokens.buttonShape,
        color = container,
        contentColor = content,
        border = BorderStroke(
            1.dp,
            if (prominent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun MatchNote(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun ConfidencePill(text: String, score: Double) {
    val tokens = LocalCueTokens.current
    val color = when (Confidence.tier(score)) {
        ConfidenceTier.ACCEPTED -> tokens.success
        ConfidenceTier.CHECK -> tokens.caution
        ConfidenceTier.IGNORED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------- clip & quality

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipQualityCard(
    state: ReviewUiState,
    input: ResolvedInput,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTrim: (Long, Long) -> Unit,
    onQuality: (QualityPreset) -> Unit,
) {
    val tokens = LocalCueTokens.current
    val quality = stringResource(state.quality.shortLabelRes())
    ReviewCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (input.kind == InputKind.VIDEO) {
                        Icons.Default.Movie
                    } else {
                        Icons.Default.MusicNote
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_clip_length_quality),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.hasTrim) {
                        SummaryChip(
                            text = stringResource(
                                R.string.review_clip_summary_short,
                                TrimClock.format(state.trimStartMs),
                                TrimClock.format(state.trimEndMs),
                                TrimClock.format(state.effectiveDurationMs ?: 0L),
                            ),
                        )
                    } else {
                        SummaryChip(
                            text = TrimClock.format(state.effectiveDurationMs ?: 0L),
                        )
                    }
                    SummaryChip(text = quality)
                }
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(FIELD_ANIM_MILLIS)) + expandVertically(tween(FIELD_ANIM_MILLIS)),
            exit = fadeOut(tween(FIELD_ANIM_MILLIS)) + shrinkVertically(tween(FIELD_ANIM_MILLIS)),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = stringResource(R.string.review_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MediaPreview(
                    input = input,
                    trimStartMs = state.trimStartMs,
                    modifier = if (input.kind == InputKind.VIDEO) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    },
                )
                if (state.hasTrim) {
                    TrimControls(
                        state = state,
                        onTrim = onTrim,
                    )
                }
                if (!state.tagOnlyEdit) {
                    Text(
                        text = stringResource(R.string.review_quality),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QualityPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.quality == preset,
                                onClick = { onQuality(preset) },
                                label = { Text(stringResource(preset.shortLabelRes())) },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = tokens.success,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.review_estimated_size_value,
                                android.text.format.Formatter.formatShortFileSize(
                                    LocalContext.current,
                                    estimateMp3Bytes(
                                        durationMs = state.effectiveDurationMs ?: 0L,
                                        preset = state.quality,
                                    ),
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A compact read-only pill for the clip card's summary row. */
@Composable
private fun SummaryChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimControls(
    state: ReviewUiState,
    onTrim: (Long, Long) -> Unit,
) {
    val durationMs = state.durationMs ?: return
    var startText by remember { mutableStateOf(TrimClock.format(state.trimStartMs)) }
    var endText by remember { mutableStateOf(TrimClock.format(state.trimEndMs)) }
    var invalid by remember { mutableStateOf(false) }
    var outOfRange by remember { mutableStateOf(false) }
    LaunchedEffect(state.trimStartMs) { startText = TrimClock.format(state.trimStartMs) }
    LaunchedEffect(state.trimEndMs) { endText = TrimClock.format(state.trimEndMs) }

    fun applyStart() {
        val parsed = TrimClock.parse(startText)
        // The handles clamp silently, which would move the end the user did not
        // touch. Reject the value instead and leave the last good window alone.
        if (parsed == null) {
            invalid = true
            outOfRange = false
        } else if (!TrimMath.acceptsStart(parsed, state.trimEndMs, durationMs)) {
            invalid = true
            outOfRange = true
        } else {
            invalid = false
            outOfRange = false
            onTrim(parsed, state.trimEndMs)
        }
    }

    fun applyEnd() {
        val parsed = TrimClock.parse(endText)
        if (parsed == null) {
            invalid = true
            outOfRange = false
        } else if (!TrimMath.acceptsEnd(parsed, state.trimStartMs, durationMs)) {
            invalid = true
            outOfRange = true
        } else {
            invalid = false
            outOfRange = false
            onTrim(state.trimStartMs, parsed)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RangeSlider(
            value = (state.trimStartMs / 1_000f)..(state.trimEndMs / 1_000f),
            onValueChange = { range ->
                onTrim((range.start * 1_000).toLong(), (range.endInclusive * 1_000).toLong())
            },
            valueRange = 0f..(durationMs / 1_000f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            track = { sliderState -> TrimTrack(sliderState) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrimTextField(
                label = stringResource(R.string.review_trim_start_short),
                value = startText,
                onValueChange = {
                    startText = it
                    invalid = false
                    outOfRange = false
                },
                onDone = ::applyStart,
                modifier = Modifier.weight(1f),
            )
            TrimTextField(
                label = stringResource(R.string.review_trim_end_short),
                value = endText,
                onValueChange = {
                    endText = it
                    invalid = false
                    outOfRange = false
                },
                onDone = ::applyEnd,
                modifier = Modifier.weight(1f),
            )
        }
        if (invalid) {
            Text(
                text = stringResource(
                    if (outOfRange) R.string.review_trim_range else R.string.review_trim_invalid,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TrimTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        shape = LocalCueTokens.current.fieldShape,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                keyboard?.hide()
                onDone()
            },
        ),
        colors = cueOutlinedFieldColors(),
    )
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

// ---------------------------------------------------------------- delete source

@Composable
private fun DeleteSourceCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    ReviewCard(
        color = if (checked) {
            error.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        borderColor = if (checked) {
            error.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = error,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.review_delete_source),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.review_delete_source_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onError,
                    checkedTrackColor = error,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- OCR details

@Composable
private fun OcrDetails(
    state: ReviewUiState,
    onUseLine: (TagField, String) -> Unit,
) {
    if (!state.hasOcrDetails) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.review_section_ocr))
    ReviewCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.TextSnippet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.review_ocr_hide else R.string.review_ocr_details,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (state.rawOcrLines.isEmpty()) {
                        stringResource(
                            if (state.ocrFailed) R.string.review_ocr_failed else R.string.review_ocr_empty,
                        )
                    } else {
                        stringResource(R.string.review_ocr_lines, state.rawOcrLines.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(FIELD_ANIM_MILLIS)) + expandVertically(tween(FIELD_ANIM_MILLIS)),
            exit = fadeOut(tween(FIELD_ANIM_MILLIS)) + shrinkVertically(tween(FIELD_ANIM_MILLIS)),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                if (state.rawOcrLines.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (state.ocrFailed) R.string.review_ocr_failed else R.string.review_ocr_empty,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.review_ocr_pick_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = LocalCueTokens.current.fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            state.rawOcrLines.forEach { line ->
                                OcrLineRow(
                                    line = line,
                                    state = state,
                                    onUse = onUseLine,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One recognised line, tappable so the user can say which field it belongs to.
 * The extractor still guesses (that is what fills the form initially); this is
 * the correction when the guess picked the wrong line.
 */
@Composable
private fun OcrLineRow(
    line: String,
    state: ReviewUiState,
    onUse: (TagField, String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val assigned = OCR_FIELDS.firstOrNull { field -> ocrMatches(state.valueFor(field), line) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LocalCueTokens.current.fieldShape)
                .clickable { menuOpen = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (assigned != null) Icons.Default.Check else Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.review_ocr_use_line),
                tint = if (assigned != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.review_ocr_use_line)) },
                enabled = false,
                onClick = {},
            )
            OCR_FIELDS.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    leadingIcon = if (field == assigned) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        menuOpen = false
                        onUse(field, line.sanitizedFor(field))
                    },
                )
            }
        }
    }
}

/** True when [line] is (or contains) the value already assigned to a field. */
private fun ocrMatches(value: String, line: String): Boolean {
    val current = ocrKey(value)
    val candidate = ocrKey(line)
    if (current.isEmpty() || candidate.isEmpty()) return false
    if (current == candidate) return true
    val shorter = minOf(current.length, candidate.length)
    val longer = maxOf(current.length, candidate.length)
    return shorter >= 4 &&
        shorter.toDouble() / longer >= 0.6 &&
        (current.contains(candidate) || candidate.contains(current))
}

private fun ocrKey(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private val OCR_FIELDS = listOf(
    TagField.TITLE,
    TagField.ARTIST,
    TagField.ALBUM,
    TagField.YEAR,
    TagField.GENRE,
)

@Composable
private fun tokensFieldShape() = LocalCueTokens.current.fieldShape

// ---------------------------------------------------------------- create bar

@Composable
private fun CreateBar(
    state: ReviewUiState,
    onSave: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    val isVideo = state.input?.kind == InputKind.VIDEO
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                // Validation and save errors live next to the button that
                // produced them; at the bottom of the scroll they were invisible.
                state.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (isVideo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (state.deleteSource) {
                                Icons.Default.DeleteOutline
                            } else {
                                Icons.Default.Movie
                            },
                            contentDescription = null,
                            tint = if (state.deleteSource) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(
                                if (state.deleteSource) {
                                    R.string.review_create_note_delete
                                } else {
                                    R.string.review_create_note_keep
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    shape = tokens.buttonShape,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = tokens.shadowElevation / 2,
                            shape = tokens.buttonShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        ),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            imageVector = if (state.tagOnlyEdit) {
                                Icons.Default.Edit
                            } else {
                                Icons.Default.GraphicEq
                            },
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(
                            when {
                                state.tagOnlyEdit -> R.string.review_save_tags
                                else -> R.string.review_create
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSheet(
    state: ReviewUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onChoose: (ArtworkCandidate) -> Unit,
    onPickDevice: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.review_artwork_search_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.artworkApplying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.artworkOffline -> SheetNote(
                    text = stringResource(R.string.review_artwork_offline),
                    color = MaterialTheme.colorScheme.error,
                )
                state.artworkSearching && state.artworkResults.isEmpty() -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.review_searching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.artworkFailed -> SheetNote(
                    text = stringResource(R.string.review_artwork_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                state.artworkSearchCompleted && state.artworkResults.isEmpty() -> SheetNote(
                    text = stringResource(R.string.review_artwork_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.artworkResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.review_cover_suggestions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ArtworkGrid(
                    candidates = if (searchOpen) {
                        state.artworkResults
                    } else {
                        state.artworkResults.take(4)
                    },
                    onChoose = onChoose,
                )
            }
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.review_artwork_search_hint)) },
                    singleLine = true,
                    shape = tokens.fieldShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            onSearch(query)
                        },
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                onSearch(query)
                            },
                            enabled = query.isNotBlank() && !state.artworkSearching,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.review_search_action),
                            )
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { searchOpen = true },
                    shape = tokens.buttonShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.review_artwork_search),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = onPickDevice,
                    shape = tokens.buttonShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.review_artwork_gallery),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (state.draft.artwork != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.review_artwork_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkGrid(
    candidates: List<ArtworkCandidate>,
    onChoose: (ArtworkCandidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { candidate ->
                    CoverCell(
                        candidate = candidate,
                        onClick = { onChoose(candidate) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SheetNote(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchesSheet(
    matches: List<MetadataMatch>,
    onDismiss: () -> Unit,
    onChoose: (MetadataMatch) -> Unit,
) {
    val tokens = LocalCueTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.review_matches_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(matches) { match ->
                    MatchSheetRow(match = match, onClick = { onChoose(match) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    state: ReviewUiState,
    initialQuery: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onChoose: (MetadataMatch) -> Unit,
) {
    val tokens = LocalCueTokens.current
    // Prefilled from the edit area so the user can correct a word instead of
    // retyping the whole song. rememberSaveable keeps that text across
    // rotation; the sheet is composed fresh each time it opens.
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length >= 2) {
            delay(SEARCH_DEBOUNCE_MILLIS)
            onSearch(term)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = tokens.sheetShape,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.review_search_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.review_search_sheet_hint)) },
                singleLine = true,
                shape = tokens.fieldShape,
                colors = cueOutlinedFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query.trim()) }),
                trailingIcon = {
                    if (state.searching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
            when {
                state.searchOffline -> SheetNote(
                    text = stringResource(R.string.review_search_offline),
                    color = MaterialTheme.colorScheme.error,
                )
                state.searching && state.searchResults.isEmpty() -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.review_searching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.searchCompleted && state.searchResults.isEmpty() -> SheetNote(
                    text = stringResource(R.string.review_no_matches),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(state.searchResults) { match ->
                        MatchSheetRow(match = match, onClick = { onChoose(match) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchSheetRow(match: MetadataMatch, onClick: () -> Unit) {
    val tokens = LocalCueTokens.current
    Surface(
        onClick = onClick,
        shape = tokens.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MatchCover(match = match, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    match.artist.takeIf { it.isNotBlank() },
                    listOfNotNull(match.album, match.year)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .takeIf { it.isNotEmpty() },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
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
            ConfidencePill(
                text = stringResource(
                    R.string.review_score_percent,
                    (match.score * 100).roundToInt(),
                ),
                score = match.score,
            )
        }
    }
}

@Composable
private fun MatchCover(match: MetadataMatch, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer().artworkRepository }
    var bitmap by remember(match.artworkUrl, match.artwork) {
        mutableStateOf(match.artwork?.let { decodeArtwork(it.bytes, maxWidth = 128) })
    }
    LaunchedEffect(match.artworkUrl) {
        if (bitmap == null && !match.artworkUrl.isNullOrBlank()) {
            bitmap = repository.preview(match.artworkUrl)
        }
    }
    Box(
        modifier = modifier
            .clip(LocalCueTokens.current.thumbnailShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- artwork grid

@Composable
private fun CoverCell(
    candidate: ArtworkCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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

// ---------------------------------------------------------------- preview

@Composable
private fun MediaPreview(
    input: ResolvedInput,
    trimStartMs: Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(input.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(input.path))))
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
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.review_play),
                        tint = LocalCueTokens.current.heroInk,
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

// ---------------------------------------------------------------- labels

@Composable
private fun SourceLabel(source: IdentificationSource) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = stringResource(source.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ReviewUiState.valueFor(field: TagField): String = when (field) {
    TagField.TITLE -> draft.title
    TagField.ARTIST -> draft.artist
    TagField.ALBUM -> draft.album
    TagField.ALBUM_ARTIST -> draft.albumArtist
    TagField.YEAR -> draft.year.orEmpty()
    TagField.GENRE -> draft.genre.orEmpty()
    TagField.TRACK_NUMBER -> draft.trackNumber?.toString().orEmpty()
    TagField.ARTWORK -> ""
}

private fun String.sanitizedFor(field: TagField): String = when (field) {
    TagField.TRACK_NUMBER -> filter(Char::isDigit).take(3)
    TagField.YEAR -> filter { it.isDigit() || it == '-' }.take(10)
    else -> this
}

private fun TagField.keyboardType(): KeyboardType = when (this) {
    TagField.TRACK_NUMBER -> KeyboardType.Number
    else -> KeyboardType.Text
}

private fun TagField.labelRes(): Int = when (this) {
    TagField.TITLE -> R.string.review_field_title
    TagField.ARTIST -> R.string.review_field_artist
    TagField.ALBUM -> R.string.review_field_album
    TagField.ALBUM_ARTIST -> R.string.review_field_album_artist
    TagField.YEAR -> R.string.review_field_year
    TagField.GENRE -> R.string.review_field_genre
    TagField.TRACK_NUMBER -> R.string.review_field_track
    TagField.ARTWORK -> R.string.review_field_artwork
}

@Composable
private fun QualityPreset.shortLabelRes(): Int = when (this) {
    QualityPreset.V0 -> R.string.review_quality_best
    QualityPreset.V2 -> R.string.review_quality_standard
    QualityPreset.CBR_256 -> R.string.review_quality_256
    QualityPreset.CBR_192 -> R.string.review_quality_192
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

@Composable
private fun cueOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

internal fun decodeArtwork(bytes: ByteArray, maxWidth: Int = 1024): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = if (bounds.outWidth > maxWidth) {
        Integer.highestOneBit(bounds.outWidth / maxWidth).coerceAtLeast(1)
    } else {
        1
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

private val PRIMARY_FIELDS = listOf(
    TagField.TITLE,
    TagField.ARTIST,
    TagField.ALBUM,
    TagField.YEAR,
)

private val EXTRA_FIELDS = listOf(
    TagField.ALBUM_ARTIST,
    TagField.GENRE,
    TagField.TRACK_NUMBER,
)

private val YEAR_PATTERN = Regex("""^\d{4}(?:-\d{2}(?:-\d{2})?)?$""")
private val TRIM_TRACK_HEIGHT = 6.dp
private const val FIELD_ANIM_MILLIS = 180
private const val SEARCH_DEBOUNCE_MILLIS = 350L
private const val EDITOR_BRING_INTO_VIEW_MILLIS = 400L

/** The field can grow by a few lines while typing; keep its actions visible. */
private const val EDITOR_TEXT_BRING_INTO_VIEW_MILLIS = 250L
