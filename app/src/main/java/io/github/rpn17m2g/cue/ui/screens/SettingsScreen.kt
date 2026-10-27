package io.github.rpn17m2g.cue.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.settings.AppLanguage
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.AppTheme
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = cueViewModel { SettingsViewModel(context.appContainer()) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsBody(
                settings = settings,
                onQuality = { quality -> viewModel.update { it.copy(defaultQuality = quality) } },
                onOutputFolder = { folder -> viewModel.update { it.copy(outputFolder = folder) } },
                onTemplate = { template -> viewModel.update { it.copy(filenameTemplate = template) } },
                onNetwork = { enabled -> viewModel.update { it.copy(networkLookups = enabled) } },
                onFingerprint = { enabled -> viewModel.update { it.copy(fingerprintEnabled = enabled) } },
                onAcoustIdKey = { key -> viewModel.update { it.copy(acoustIdKey = key) } },
                onScanWindow = { seconds -> viewModel.update { it.copy(scanWindowSeconds = seconds) } },
                onOcrInterval = { intervalMs -> viewModel.update { it.copy(ocrIntervalMs = intervalMs) } },
            )
        }
    }
}

@Composable
private fun SettingsBody(
    settings: AppSettings,
    onQuality: (QualityPreset) -> Unit,
    onOutputFolder: (String) -> Unit,
    onTemplate: (String) -> Unit,
    onNetwork: (Boolean) -> Unit,
    onFingerprint: (Boolean) -> Unit,
    onAcoustIdKey: (String) -> Unit,
    onScanWindow: (Int) -> Unit,
    onOcrInterval: (Long) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_quality),
        style = MaterialTheme.typography.titleLarge,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QualityPreset.entries.forEach { preset ->
            FilterChip(
                selected = settings.defaultQuality == preset,
                onClick = { onQuality(preset) },
                label = { Text(stringResource(preset.labelRes())) },
            )
        }
    }
    OutlinedTextField(
        value = settings.outputFolder,
        onValueChange = onOutputFolder,
        label = { Text(stringResource(R.string.settings_output_folder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = settings.filenameTemplate,
        onValueChange = onTemplate,
        label = { Text(stringResource(R.string.settings_filename_template)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    HorizontalDivider()
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
    ToggleRow(
        label = stringResource(R.string.settings_network),
        checked = settings.networkLookups,
        onCheckedChange = onNetwork,
    )
    ToggleRow(
        label = stringResource(R.string.settings_fingerprint),
        checked = settings.fingerprintEnabled,
        onCheckedChange = onFingerprint,
    )
    OutlinedTextField(
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
        singleLine = true,
    )
    HorizontalDivider()
    PlaceholderRow(
        label = stringResource(R.string.settings_language),
        value = stringResource(settings.language.labelRes()),
    )
    PlaceholderRow(
        label = stringResource(R.string.settings_theme),
        value = stringResource(settings.theme.labelRes()),
    )
    Text(
        text = stringResource(R.string.settings_phase5_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            val digits = input.filter(Char::isDigit).take(3)
            text = digits
            digits.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        supportingText = {
            Text(stringResource(R.string.settings_range_hint, range.first, range.last))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun PlaceholderRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.HEBREW -> R.string.settings_language_hebrew
}

@Composable
private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
}
