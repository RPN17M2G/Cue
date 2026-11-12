package io.github.rpn17m2g.cue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.settings.AppLanguage
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.AppTheme
import io.github.rpn17m2g.cue.settings.CuePalette
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.theme.CueStyle
import io.github.rpn17m2g.cue.ui.theme.spec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = cueViewModel { SettingsViewModel(context.appContainer()) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyCheck by viewModel.keyCheck.collectAsStateWithLifecycle()
    val acrCloudCheck by viewModel.acrCloudCheck.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsBody(
                settings = settings,
                onQuality = { quality -> viewModel.update { it.copy(defaultQuality = quality) } },
                onOutputFolder = { folder -> viewModel.update { it.copy(outputFolder = folder) } },
                onLanguage = { language -> viewModel.update { it.copy(language = language) } },
                onPalette = { palette -> viewModel.update { it.copy(palette = palette) } },
                onTheme = { theme -> viewModel.update { it.copy(theme = theme) } },
                onNetwork = { enabled -> viewModel.update { it.copy(networkLookups = enabled) } },
                onFingerprint = { enabled -> viewModel.update { it.copy(fingerprintEnabled = enabled) } },
                onAcoustIdKey = { key ->
                    viewModel.clearKeyCheck()
                    viewModel.update { it.copy(acoustIdKey = key) }
                },
                keyCheck = keyCheck,
                onCheckKey = viewModel::checkAcoustIdKey,
                onAcrCloudHost = { host ->
                    viewModel.clearAcrCloudCheck()
                    viewModel.update { it.copy(acrCloudHost = host) }
                },
                onAcrCloudAccessKey = { key ->
                    viewModel.clearAcrCloudCheck()
                    viewModel.update { it.copy(acrCloudAccessKey = key) }
                },
                onAcrCloudAccessSecret = { secret ->
                    viewModel.clearAcrCloudCheck()
                    viewModel.update { it.copy(acrCloudAccessSecret = secret) }
                },
                acrCloudCheck = acrCloudCheck,
                onCheckAcrCloud = viewModel::checkAcrCloud,
                onEnrichment = { enabled -> viewModel.update { it.copy(enrichmentEnabled = enabled) } },
                onTemplate = { template -> viewModel.update { it.copy(filenameTemplate = template) } },
                onDeleteSource = { enabled -> viewModel.update { it.copy(deleteSourceAfterSave = enabled) } },
                onScanWindow = { seconds -> viewModel.update { it.copy(scanWindowSeconds = seconds) } },
                onOcrInterval = { intervalMs -> viewModel.update { it.copy(ocrIntervalMs = intervalMs) } },
                onHttpTimeout = { timeoutMs -> viewModel.update { it.copy(httpTimeoutMs = timeoutMs) } },
                onHttpRetries = { retries -> viewModel.update { it.copy(httpRetries = retries) } },
            )
        }
    }
}

@Composable
private fun SettingsBody(
    settings: AppSettings,
    onQuality: (QualityPreset) -> Unit,
    onOutputFolder: (String) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onPalette: (CuePalette) -> Unit,
    onTheme: (AppTheme) -> Unit,
    onNetwork: (Boolean) -> Unit,
    onFingerprint: (Boolean) -> Unit,
    onAcoustIdKey: (String) -> Unit,
    keyCheck: KeyCheck,
    onCheckKey: () -> Unit,
    onAcrCloudHost: (String) -> Unit,
    onAcrCloudAccessKey: (String) -> Unit,
    onAcrCloudAccessSecret: (String) -> Unit,
    acrCloudCheck: KeyCheck,
    onCheckAcrCloud: () -> Unit,
    onEnrichment: (Boolean) -> Unit,
    onTemplate: (String) -> Unit,
    onDeleteSource: (Boolean) -> Unit,
    onScanWindow: (Int) -> Unit,
    onOcrInterval: (Long) -> Unit,
    onHttpTimeout: (Long) -> Unit,
    onHttpRetries: (Int) -> Unit,
) {
    SettingsSection(R.string.settings_quality) {
        ChoiceChips(
            options = QualityPreset.entries,
            selected = settings.defaultQuality,
            labelRes = QualityPreset::labelRes,
            onSelect = onQuality,
        )
        StoredTextField(
            value = settings.outputFolder,
            onValueChange = onOutputFolder,
            label = { Text(stringResource(R.string.settings_output_folder)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SettingsSection(R.string.settings_language) {
        ChoiceChips(
            options = AppLanguage.entries,
            selected = settings.language,
            labelRes = AppLanguage::labelRes,
            onSelect = onLanguage,
        )
    }

    SettingsSection(R.string.settings_appearance) {
        Text(
            text = stringResource(R.string.settings_palette),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 2.dp),
        )
        PalettePicker(
            selected = settings.palette,
            dark = settings.theme.resolvesDark(),
            onSelect = onPalette,
        )
        Text(
            text = stringResource(R.string.settings_palette_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsDivider()
        Text(
            text = stringResource(R.string.settings_mode),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 2.dp),
        )
        ChoiceChips(
            options = AppTheme.entries,
            selected = settings.theme,
            labelRes = AppTheme::labelRes,
            onSelect = onTheme,
        )
    }

    SettingsSection(R.string.settings_after_processing) {
        ToggleRow(
            label = stringResource(R.string.settings_delete_source),
            checked = settings.deleteSourceAfterSave,
            onCheckedChange = onDeleteSource,
        )
        Text(
            text = stringResource(R.string.settings_delete_source_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSection(R.string.settings_identification) {
        ToggleRow(
            label = stringResource(R.string.settings_network),
            checked = settings.networkLookups,
            onCheckedChange = onNetwork,
        )
        SettingsDivider()
        ToggleRow(
            label = stringResource(R.string.settings_fingerprint),
            checked = settings.fingerprintEnabled,
            onCheckedChange = onFingerprint,
        )
        SettingsDivider()
        StoredTextField(
            value = settings.acoustIdKey,
            onValueChange = onAcoustIdKey,
            label = { Text(stringResource(R.string.settings_acoustid_key)) },
            supportingText = {
                Text(
                    stringResource(
                        if (settings.acoustIdKey.isBlank()) {
                            R.string.settings_acoustid_missing
                        } else {
                            R.string.settings_acoustid_hint
                        },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onCheckKey,
                enabled = settings.acoustIdKey.isNotBlank() && keyCheck != KeyCheck.CHECKING,
            ) {
                if (keyCheck == KeyCheck.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                        if (keyCheck == KeyCheck.CHECKING) {
                            R.string.settings_acoustid_checking
                        } else {
                            R.string.settings_acoustid_check
                        },
                    ),
                )
            }
        }
        val checkMessage = when (keyCheck) {
            KeyCheck.VALID -> R.string.settings_acoustid_valid
            KeyCheck.REJECTED -> R.string.settings_acoustid_rejected
            KeyCheck.UNAVAILABLE -> R.string.settings_acoustid_unavailable
            KeyCheck.ERROR -> R.string.settings_acoustid_error
            KeyCheck.IDLE, KeyCheck.CHECKING -> null
        }
        if (checkMessage != null) {
            Text(
                text = stringResource(checkMessage),
                style = MaterialTheme.typography.bodySmall,
                color = when (keyCheck) {
                    KeyCheck.VALID -> MaterialTheme.colorScheme.primary
                    KeyCheck.REJECTED, KeyCheck.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        SettingsDivider()
        ToggleRow(
            label = stringResource(R.string.settings_enrichment),
            checked = settings.enrichmentEnabled,
            onCheckedChange = onEnrichment,
        )
        Text(
            text = stringResource(R.string.settings_enrichment_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSection(R.string.settings_acrcloud) {
        Text(
            text = stringResource(R.string.settings_acrcloud_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StoredTextField(
            value = settings.acrCloudHost,
            onValueChange = onAcrCloudHost,
            label = { Text(stringResource(R.string.settings_acrcloud_host)) },
            supportingText = {
                Text(
                    stringResource(
                        if (settings.acrCloudHost.isBlank()) {
                            R.string.settings_acrcloud_missing
                        } else {
                            R.string.settings_acrcloud_host_hint
                        },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        StoredTextField(
            value = settings.acrCloudAccessKey,
            onValueChange = onAcrCloudAccessKey,
            label = { Text(stringResource(R.string.settings_acrcloud_access_key)) },
            modifier = Modifier.fillMaxWidth(),
        )
        StoredTextField(
            value = settings.acrCloudAccessSecret,
            onValueChange = onAcrCloudAccessSecret,
            label = { Text(stringResource(R.string.settings_acrcloud_access_secret)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onCheckAcrCloud,
                enabled = settings.acrCloudConfigured && acrCloudCheck != KeyCheck.CHECKING,
            ) {
                if (acrCloudCheck == KeyCheck.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                        if (acrCloudCheck == KeyCheck.CHECKING) {
                            R.string.settings_acoustid_checking
                        } else {
                            R.string.settings_acoustid_check
                        },
                    ),
                )
            }
        }
        val acrCloudMessage = when (acrCloudCheck) {
            KeyCheck.VALID -> R.string.settings_acrcloud_valid
            KeyCheck.REJECTED -> R.string.settings_acrcloud_rejected
            KeyCheck.UNAVAILABLE -> R.string.settings_acoustid_unavailable
            KeyCheck.ERROR -> R.string.settings_acoustid_error
            KeyCheck.IDLE, KeyCheck.CHECKING -> null
        }
        if (acrCloudMessage != null) {
            Text(
                text = stringResource(acrCloudMessage),
                style = MaterialTheme.typography.bodySmall,
                color = when (acrCloudCheck) {
                    KeyCheck.VALID -> MaterialTheme.colorScheme.primary
                    KeyCheck.REJECTED, KeyCheck.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    SettingsSection(R.string.settings_advanced) {
        StoredTextField(
            value = settings.filenameTemplate,
            onValueChange = onTemplate,
            label = { Text(stringResource(R.string.settings_filename_template)) },
            modifier = Modifier.fillMaxWidth(),
        )
        IntSettingField(
            value = settings.scanWindowSeconds,
            label = stringResource(R.string.settings_scan_window),
            range = AppSettings.SCAN_WINDOW_RANGE,
            onValue = onScanWindow,
        )
        LongSettingField(
            value = settings.ocrIntervalMs,
            label = stringResource(R.string.settings_ocr_interval),
            range = AppSettings.OCR_INTERVAL_RANGE,
            onValue = onOcrInterval,
        )
        LongSettingField(
            value = settings.httpTimeoutMs,
            label = stringResource(R.string.settings_http_timeout),
            range = AppSettings.HTTP_TIMEOUT_RANGE,
            onValue = onHttpTimeout,
        )
        IntSettingField(
            value = settings.httpRetries,
            label = stringResource(R.string.settings_http_retries),
            range = AppSettings.HTTP_RETRIES_RANGE,
            onValue = onHttpRetries,
        )
    }
}

/**
 * A text setting that lives in DataStore.
 *
 * Binding the field straight to the stored value drops keystrokes: the write
 * is asynchronous, so the next letter can arrive before the flow emits the
 * previous one and the field re-renders with stale text. An API key typed that
 * way ends up scrambled and then rejected. The local draft makes typing
 * immediate; the stored value is adopted only while the field is not focused,
 * so an external change still lands.
 */
@Composable
private fun StoredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var draft by rememberSaveable { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused && value != draft) draft = value
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { input ->
            draft = input
            onValueChange(input)
        },
        label = label,
        supportingText = supportingText,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = settingsFieldColors(),
    )
}

@Composable
private fun SettingsSection(
    titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * Two columns of real miniatures, not swatches: each card draws the palette's
 * hero gradient, its ink and its primary exactly as the details screen would.
 * Tapping applies the palette immediately, so the card that is no longer
 * selected is itself the proof that the change took.
 */
@Composable
private fun PalettePicker(
    selected: CuePalette,
    dark: Boolean,
    onSelect: (CuePalette) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CuePalette.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { palette ->
                    PaletteCard(
                        palette = palette,
                        selected = palette == selected,
                        dark = dark,
                        onSelect = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: CuePalette,
    selected: Boolean,
    dark: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = palette.spec
    val preview = if (dark) spec.dark else spec.light
    val label = stringResource(palette.labelRes())
    val character = stringResource(spec.style.labelRes())
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "$label, $character" }
            .background(preview.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(9.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(spec.heroGradient)),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Bar(widthFraction = 0.62f, height = 5.dp, color = Color.White.copy(alpha = 0.95f))
                Bar(widthFraction = 0.40f, height = 3.dp, color = Color.White.copy(alpha = 0.72f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Bar(0.78f, 5.dp, preview.onSurface.copy(alpha = 0.85f))
        Spacer(Modifier.height(5.dp))
        Bar(0.52f, 4.dp, preview.onSurfaceVariant.copy(alpha = 0.75f))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 12.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(preview.primary),
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = preview.onSurface,
            maxLines = 1,
        )
        Text(
            text = character,
            style = MaterialTheme.typography.labelSmall,
            color = preview.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Bar(widthFraction: Float, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}

@Composable
private fun AppTheme.resolvesDark(): Boolean = when (this) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(labelRes(option))) },
            )
        }
    }
}

@Composable
private fun IntSettingField(
    value: Int,
    label: String,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(5)
            text = digits
            digits.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        supportingText = {
            Text(stringResource(R.string.settings_range_hint, range.first, range.last))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = settingsFieldColors(),
    )
}

@Composable
private fun LongSettingField(
    value: Long,
    label: String,
    range: LongRange,
    onValue: (Long) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(5)
            text = digits
            digits.toLongOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        supportingText = {
            Text(stringResource(R.string.settings_range_hint, range.first, range.last))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = settingsFieldColors(),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun QualityPreset.labelRes(): Int = when (this) {
    QualityPreset.V0 -> R.string.quality_v0
    QualityPreset.V2 -> R.string.quality_v2
    QualityPreset.CBR_256 -> R.string.quality_cbr256
    QualityPreset.CBR_192 -> R.string.quality_cbr192
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.HEBREW -> R.string.settings_language_hebrew
}

private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
}

private fun CuePalette.labelRes(): Int = when (this) {
    CuePalette.MINT -> R.string.palette_mint
    CuePalette.LAGOON -> R.string.palette_lagoon
    CuePalette.NAVY -> R.string.palette_navy
    CuePalette.DEEP_TEAL -> R.string.palette_deep_teal
}

private fun CueStyle.labelRes(): Int = when (this) {
    CueStyle.CHEERFUL -> R.string.palette_style_cheerful
    CueStyle.PROFESSIONAL -> R.string.palette_style_professional
}
