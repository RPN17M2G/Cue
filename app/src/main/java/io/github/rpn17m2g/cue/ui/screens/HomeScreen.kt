package io.github.rpn17m2g.cue.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.library.LibraryEntry
import io.github.rpn17m2g.cue.data.library.LibraryGrouping
import io.github.rpn17m2g.cue.data.library.LibraryKind
import io.github.rpn17m2g.cue.data.library.LibrarySource
import io.github.rpn17m2g.cue.data.library.LibraryTab
import io.github.rpn17m2g.cue.data.library.MediaPermissions
import io.github.rpn17m2g.cue.data.library.requiredMediaPermissions
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.theme.CueMark
import io.github.rpn17m2g.cue.ui.theme.LocalCueTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onStartReview: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenError: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { HomeViewModel(context.appContainer()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()

    val activity = remember(context) { context.findActivity() }
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // A denial with "don't ask again" cannot be followed by another system
        // dialog, so the card switches to the app-settings shortcut instead of
        // a button that would silently do nothing.
        val requested = requiredMediaPermissions()
        permissionBlocked = result.isNotEmpty() &&
            result.values.none { it } &&
            activity != null &&
            requested.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        viewModel.refresh()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.resolve(uri, onResolved = onStartReview, onError = onOpenError)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    HomeContent(
        state = state,
        resolving = resolving,
        searchOpen = searchOpen,
        query = query,
        permissionBlocked = permissionBlocked,
        onQueryChange = { query = it },
        onToggleSearch = {
            searchOpen = !searchOpen
            if (!searchOpen) query = ""
        },
        onSelectTab = viewModel::selectTab,
        onOpenEntry = { entry ->
            viewModel.resolve(entry.uri, onResolved = onStartReview, onError = onOpenError)
        },
        onAddFile = { picker.launch(INPUT_MIME_TYPES) },
        onGrantAccess = {
            if (permissionBlocked) {
                openAppSettings(context)
            } else {
                permissionLauncher.launch(requiredMediaPermissions())
            }
        },
        onOpenSettings = onOpenSettings,
        loadThumbnail = viewModel::thumbnail,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    resolving: Boolean,
    searchOpen: Boolean,
    query: String,
    permissionBlocked: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    onAddFile: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    loadThumbnail: suspend (Uri) -> Bitmap?,
) {
    val tokens = LocalCueTokens.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFile,
                shape = tokens.buttonShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = tokens.shadowElevation,
                    pressedElevation = tokens.shadowElevation / 2,
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.home_add_file),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        },
    ) { padding ->
        val background = MaterialTheme.colorScheme.background
        val darkBackground = background.luminance() < 0.5f
        val washTop = MaterialTheme.colorScheme.primary.copy(
            alpha = if (darkBackground) 0.18f else 0.12f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to washTop,
                                0.6f to washTop.copy(alpha = washTop.alpha * 0.35f),
                                1f to Color.Transparent,
                                startY = 0f,
                                endY = size.height,
                            ),
                        )
                    },
            ) {
                Column {
                    HomeHeader(
                        searchOpen = searchOpen,
                        enabled = !resolving,
                        onToggleSearch = onToggleSearch,
                        onOpenSettings = onOpenSettings,
                    )
                    SearchField(
                        visible = searchOpen,
                        query = query,
                        onQueryChange = onQueryChange,
                        onClear = { onQueryChange("") },
                    )
                    TabBar(
                        tab = state.tab,
                        entries = state.entries,
                        onSelect = onSelectTab,
                    )
                    PreparingBar(visible = resolving)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                GalleryBody(
                    state = state,
                    query = query,
                    enabled = !resolving,
                    permissionBlocked = permissionBlocked,
                    onOpenEntry = onOpenEntry,
                    onAddFile = onAddFile,
                    onGrantAccess = onGrantAccess,
                    loadThumbnail = loadThumbnail,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    searchOpen: Boolean,
    enabled: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tokens = LocalCueTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = tokens.titleWeight,
                        letterSpacing = tokens.titleTracking,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                CueMark(modifier = Modifier.size(22.dp))
            }
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                // onSurface rather than onSurfaceVariant: the header wash
                // lightens the background enough that the muted tone drops
                // below 4.5:1 on the light palettes.
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(onClick = onToggleSearch, enabled = enabled) {
            Icon(
                imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = stringResource(
                    if (searchOpen) R.string.home_search_clear else R.string.home_search,
                ),
            )
        }
        Spacer(Modifier.width(2.dp))
        FilledTonalIconButton(onClick = onOpenSettings, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.home_settings),
            )
        }
    }
}

@Composable
private fun SearchField(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) focusRequester.requestFocus()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(HOME_FADE_MILLIS)) + expandVertically(tween(HOME_FADE_MILLIS)),
        exit = fadeOut(tween(HOME_FADE_MILLIS)) + shrinkVertically(tween(HOME_FADE_MILLIS)),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.home_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = LocalCueTokens.current.fieldShape,
        )
    }
}

@Composable
private fun TabBar(
    tab: LibraryTab,
    entries: List<LibraryEntry>,
    onSelect: (LibraryTab) -> Unit,
) {
    val counts = remember(entries) {
        mapOf(
            LibraryTab.RECENT to LibraryGrouping.recent(entries).size,
            LibraryTab.RECORDINGS to LibraryGrouping.recordings(entries).size,
            LibraryTab.MUSIC to LibraryGrouping.music(entries).size,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(2.dp)
                .selectableGroup(),
        ) {
            LibraryTab.entries.forEach { item ->
                TabPill(
                    tab = item,
                    count = counts[item] ?: 0,
                    selected = tab == item,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    tab: LibraryTab,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(HOME_FADE_MILLIS),
        label = "tabContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(HOME_FADE_MILLIS),
        label = "tabContent",
    )
    val tokens = LocalCueTokens.current
    val shadow = if (selected) {
        Modifier.shadow(
            elevation = tokens.shadowElevation / 2,
            shape = CircleShape,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        )
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .then(shadow)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(tab.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                // Three equal pills at 360 dp leave "Recordings" ~60 dp once
                // the count badge is measured; let the label ellipsize instead
                // of pushing the badge out of the pill.
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            CountBadge(count = count, selected = selected)
        }
    }
}

@Composable
private fun CountBadge(count: Int, selected: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        },
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun PreparingBar(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(HOME_FADE_MILLIS)) + expandVertically(tween(HOME_FADE_MILLIS)),
        exit = fadeOut(tween(HOME_FADE_MILLIS)) + shrinkVertically(tween(HOME_FADE_MILLIS)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.home_preparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GalleryBody(
    state: HomeUiState,
    query: String,
    enabled: Boolean,
    permissionBlocked: Boolean,
    onOpenEntry: (LibraryEntry) -> Unit,
    onAddFile: () -> Unit,
    onGrantAccess: () -> Unit,
    loadThumbnail: suspend (Uri) -> Bitmap?,
) {
    val needsPermission = when (state.tab) {
        LibraryTab.RECORDINGS -> !state.permissions.video
        LibraryTab.MUSIC -> !state.permissions.audio
        LibraryTab.RECENT -> !state.permissions.any
    }
    val entries = remember(state.entries, state.tab, query) {
        LibraryGrouping.forTab(state.entries, state.tab).filter { entry ->
            query.isBlank() ||
                entry.title.contains(query, ignoreCase = true) ||
                entry.artist?.contains(query, ignoreCase = true) == true
        }
    }

    when {
        !state.loaded -> LoadingState()
        needsPermission -> PermissionState(
            permissions = state.permissions,
            blocked = permissionBlocked,
            onGrantAccess = onGrantAccess,
        )
        entries.isEmpty() && query.isNotBlank() -> EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.home_empty_search_title),
            body = stringResource(R.string.home_empty_search_body, query),
            onAddFile = null,
        )
        entries.isEmpty() -> EmptyState(
            icon = if (state.tab == LibraryTab.MUSIC) Icons.Default.MusicNote else Icons.Default.Movie,
            title = stringResource(state.tab.emptyTitleRes()),
            body = stringResource(state.tab.emptyBodyRes()),
            onAddFile = onAddFile,
        )
        else -> EntryList(
            entries = entries,
            enabled = enabled,
            onOpenEntry = onOpenEntry,
            loadThumbnail = loadThumbnail,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(
    entries: List<LibraryEntry>,
    enabled: Boolean,
    onOpenEntry: (LibraryEntry) -> Unit,
    loadThumbnail: suspend (Uri) -> Bitmap?,
) {
    val groups = remember(entries) { LibraryGrouping.groupByDay(entries) }
    val today = LocalDate.now()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            stickyHeader(key = "day-${group.date}") {
                DayHeader(
                    label = dayLabel(group.date, today),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 16.dp, bottom = 6.dp),
                )
            }
            items(group.entries, key = { it.uri.toString() }) { entry ->
                EntryRow(
                    entry = entry,
                    enabled = enabled,
                    onClick = { onOpenEntry(entry) },
                    loadThumbnail = loadThumbnail,
                )
            }
        }
    }
}

/**
 * Today and Yesterday stay words; every other day gets its real date so a
 * library with months of history no longer collapses into one "Earlier" pile.
 * The skeleton is resolved by the platform, which keeps the order and the month
 * names right in Hebrew as well as English.
 */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return stringResource(R.string.home_day_today)
    if (date == today.minusDays(1)) return stringResource(R.string.home_day_yesterday)
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, today, locale) {
        val skeleton = if (date.year == today.year) "EEEMMMd" else "yMMMEd"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }
}

@Composable
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        // Latin small-caps tracking reads well in English but separates Hebrew
        // letters awkwardly, so drop it for RTL locales.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Text(
            text = label.uppercase(),
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

@Composable
private fun EntryRow(
    entry: LibraryEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    loadThumbnail: suspend (Uri) -> Bitmap?,
) {
    val tokens = LocalCueTokens.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = tokens.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = tokens.shadowElevation / 2,
                shape = tokens.cardShape,
                ambientColor = tokens.glow.copy(alpha = 0.20f),
                spotColor = tokens.glow.copy(alpha = 0.26f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(entry = entry, loadThumbnail = loadThumbnail)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondary = listOfNotNull(
                    entry.artist,
                    entry.durationMs?.let { DateUtils.formatElapsedTime(it / 1_000) },
                ).joinToString(" · ")
                if (secondary.isNotEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(7.dp))
                SourceChip(source = entry.source)
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Thumbnail(
    entry: LibraryEntry,
    loadThumbnail: suspend (Uri) -> Bitmap?,
) {
    var image by remember(entry.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.uri) {
        image = loadThumbnail(entry.uri)
    }
    val tokens = LocalCueTokens.current
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(tokens.thumbnailShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = tokens.thumbnailShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (entry.kind == LibraryKind.VIDEO) {
                    Icons.Default.Movie
                } else {
                    Icons.Default.MusicNote
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        if (entry.kind == LibraryKind.VIDEO) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
                contentColor = tokens.heroInk,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: LibrarySource) {
    val (container, content) = when (source) {
        LibrarySource.CUE_OUTPUT -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        LibrarySource.RECORDING,
        LibrarySource.MUSIC,
        -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = stringResource(source.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionState(
    permissions: MediaPermissions,
    blocked: Boolean,
    onGrantAccess: () -> Unit,
) {
    CenteredMessage(
        icon = Icons.Default.VideoLibrary,
        title = stringResource(R.string.home_permission_title),
        body = stringResource(
            if (permissions.any) R.string.home_permission_partial else R.string.home_permission_body,
        ),
        action = {
            Button(
                onClick = onGrantAccess,
                shape = LocalCueTokens.current.buttonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(
                        if (blocked) R.string.home_permission_settings else R.string.home_permission_action,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    onAddFile: (() -> Unit)?,
) {
    CenteredMessage(
        icon = icon,
        title = title,
        body = body,
        action = {
            if (onAddFile != null) {
                OutlinedButton(
                    onClick = onAddFile,
                    shape = LocalCueTokens.current.buttonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_empty_add),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    )
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.RECENT -> R.string.home_tab_recent
    LibraryTab.RECORDINGS -> R.string.home_tab_recordings
    LibraryTab.MUSIC -> R.string.home_tab_music
}

private fun LibraryTab.emptyTitleRes(): Int = when (this) {
    LibraryTab.RECENT -> R.string.home_empty_recent_title
    LibraryTab.RECORDINGS -> R.string.home_empty_recordings_title
    LibraryTab.MUSIC -> R.string.home_empty_music_title
}

private fun LibraryTab.emptyBodyRes(): Int = when (this) {
    LibraryTab.RECENT -> R.string.home_empty_recent_body
    LibraryTab.RECORDINGS -> R.string.home_empty_recordings_body
    LibraryTab.MUSIC -> R.string.home_empty_music_body
}

private fun LibrarySource.labelRes(): Int = when (this) {
    LibrarySource.RECORDING -> R.string.home_chip_recording
    LibrarySource.CUE_OUTPUT -> R.string.home_chip_cue
    LibrarySource.MUSIC -> R.string.home_chip_music
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val INPUT_MIME_TYPES = arrayOf("video/*", "audio/*")

private const val HOME_FADE_MILLIS = 200
