package io.github.rpn17m2g.cue.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.rpn17m2g.cue.BuildConfig
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.settings.AppLanguage
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.AppTheme
import io.github.rpn17m2g.cue.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cueDataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")

class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {

    private val settingsFlow: Flow<AppSettings> = context.cueDataStore.data.map(::mapPreferences)

    override val settings: Flow<AppSettings> = settingsFlow

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settingsFlow.first())
        context.cueDataStore.edit { preferences ->
            preferences[Keys.SCAN_WINDOW] = next.scanWindowSeconds
            preferences[Keys.QUALITY] = next.defaultQuality.name
            preferences[Keys.OUTPUT_FOLDER] = next.outputFolder
            preferences[Keys.NETWORK] = next.networkLookups
            preferences[Keys.FINGERPRINT] = next.fingerprintEnabled
            preferences[Keys.ACOUSTID_KEY] = next.acoustIdKey
            preferences[Keys.LANGUAGE] = next.language.name
            preferences[Keys.THEME] = next.theme.name
            preferences[Keys.FILENAME_TEMPLATE] = next.filenameTemplate
            preferences[Keys.OCR_INTERVAL] = next.ocrIntervalMs
            preferences[Keys.HTTP_TIMEOUT] = next.httpTimeoutMs
            preferences[Keys.HTTP_RETRIES] = next.httpRetries
        }
    }

    private fun mapPreferences(preferences: Preferences): AppSettings = AppSettings(
        scanWindowSeconds = preferences[Keys.SCAN_WINDOW]
            ?: AppSettings.DEFAULT_SCAN_WINDOW_SECONDS,
        defaultQuality = preferences[Keys.QUALITY]
            ?.let { name -> QualityPreset.entries.firstOrNull { it.name == name } }
            ?: QualityPreset.V0,
        outputFolder = preferences[Keys.OUTPUT_FOLDER] ?: AppSettings.DEFAULT_OUTPUT_FOLDER,
        networkLookups = preferences[Keys.NETWORK] ?: true,
        fingerprintEnabled = preferences[Keys.FINGERPRINT] ?: true,
        acoustIdKey = preferences[Keys.ACOUSTID_KEY] ?: BuildConfig.ACOUSTID_API_KEY,
        language = preferences[Keys.LANGUAGE]
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.SYSTEM,
        theme = preferences[Keys.THEME]
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.SYSTEM,
        filenameTemplate = preferences[Keys.FILENAME_TEMPLATE]
            ?: AppSettings.DEFAULT_FILENAME_TEMPLATE,
        ocrIntervalMs = preferences[Keys.OCR_INTERVAL] ?: AppSettings.DEFAULT_OCR_INTERVAL_MS,
        httpTimeoutMs = preferences[Keys.HTTP_TIMEOUT] ?: AppSettings.DEFAULT_HTTP_TIMEOUT_MS,
        httpRetries = preferences[Keys.HTTP_RETRIES] ?: AppSettings.DEFAULT_HTTP_RETRIES,
    )

    private object Keys {
        val SCAN_WINDOW = intPreferencesKey("scan_window_seconds")
        val QUALITY = stringPreferencesKey("default_quality")
        val OUTPUT_FOLDER = stringPreferencesKey("output_folder")
        val NETWORK = booleanPreferencesKey("network_lookups")
        val FINGERPRINT = booleanPreferencesKey("fingerprint_enabled")
        val ACOUSTID_KEY = stringPreferencesKey("acoustid_key")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val FILENAME_TEMPLATE = stringPreferencesKey("filename_template")
        val OCR_INTERVAL = longPreferencesKey("ocr_interval_ms")
        val HTTP_TIMEOUT = longPreferencesKey("http_timeout_ms")
        val HTTP_RETRIES = intPreferencesKey("http_retries")
    }
}
