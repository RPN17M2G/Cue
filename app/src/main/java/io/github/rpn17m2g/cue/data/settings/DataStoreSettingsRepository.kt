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
import io.github.rpn17m2g.cue.settings.AcoustIdKey
import io.github.rpn17m2g.cue.settings.AcrCloudCredentials
import io.github.rpn17m2g.cue.settings.AppLanguage
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.AppTheme
import io.github.rpn17m2g.cue.settings.CuePalette
import io.github.rpn17m2g.cue.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cueDataStore: DataStore<Preferences> by preferencesDataStore(name = "cue_settings")

class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {

    private val settingsFlow: Flow<AppSettings> = context.cueDataStore.data.map(::mapPreferences)

    override val settings: Flow<AppSettings> = settingsFlow

    /**
     * Reads and writes inside the same [edit] block. Reading the current value
     * outside it made this a read-modify-write race: two updates in flight (fast
     * typing in Settings fires one per keystroke) both started from the same
     * snapshot, so the later write put back the older text and the field visibly
     * jumped backwards. DataStore serializes [edit] calls, which removes the race.
     */
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.cueDataStore.edit { preferences ->
            val next = transform(mapPreferences(preferences))
            preferences[Keys.SCAN_WINDOW] = next.scanWindowSeconds
            preferences[Keys.QUALITY] = next.defaultQuality.name
            preferences[Keys.OUTPUT_FOLDER] = next.outputFolder
            preferences[Keys.NETWORK] = next.networkLookups
            preferences[Keys.FINGERPRINT] = next.fingerprintEnabled
            preferences[Keys.ACOUSTID_KEY] = next.acoustIdKey
            preferences[Keys.LANGUAGE] = next.language.name
            preferences[Keys.THEME] = next.theme.name
            preferences[Keys.PALETTE] = next.palette.name
            preferences[Keys.FILENAME_TEMPLATE] = next.filenameTemplate
            preferences[Keys.DELETE_SOURCE] = next.deleteSourceAfterSave
            preferences[Keys.OCR_INTERVAL] = next.ocrIntervalMs
            preferences[Keys.HTTP_TIMEOUT] = next.httpTimeoutMs
            preferences[Keys.HTTP_RETRIES] = next.httpRetries
            preferences[Keys.ACRCLOUD_HOST] = next.acrCloudHost
            preferences[Keys.ACRCLOUD_ACCESS_KEY] = next.acrCloudAccessKey
            preferences[Keys.ACRCLOUD_ACCESS_SECRET] = next.acrCloudAccessSecret
            preferences[Keys.ENRICHMENT] = next.enrichmentEnabled
        }
    }
}

/**
 * Maps the persisted entries onto [AppSettings]. Kept outside the class, and
 * free of any [Context], so the defaults — in particular the AcoustID key
 * fallback — can be unit tested without a device.
 */
internal fun mapPreferences(preferences: Preferences): AppSettings = AppSettings(
    scanWindowSeconds = preferences[Keys.SCAN_WINDOW]
        ?: AppSettings.DEFAULT_SCAN_WINDOW_SECONDS,
    defaultQuality = preferences[Keys.QUALITY]
        ?.let { name -> QualityPreset.entries.firstOrNull { it.name == name } }
        ?: QualityPreset.V0,
    outputFolder = preferences[Keys.OUTPUT_FOLDER] ?: AppSettings.DEFAULT_OUTPUT_FOLDER,
    networkLookups = preferences[Keys.NETWORK] ?: true,
    fingerprintEnabled = preferences[Keys.FINGERPRINT] ?: true,
    // A blank entry means the user never saved a key (or cleared it), so the
    // build-time default still applies: "absent" and "" have to behave the
    // same way, otherwise an early build that stored the default writes ""
    // and disables fingerprint lookup for good.
    acoustIdKey = AcoustIdKey.orBuildDefault(
        stored = preferences[Keys.ACOUSTID_KEY],
        buildDefault = BuildConfig.ACOUSTID_API_KEY,
    ),
    language = preferences[Keys.LANGUAGE]
        ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
        ?: AppLanguage.SYSTEM,
    theme = preferences[Keys.THEME]
        ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
        ?: AppTheme.SYSTEM,
    // An unknown or missing value falls back to Mint, so this key needs no
    // migration and a downgrade cannot break the theme.
    palette = preferences[Keys.PALETTE]
        ?.let { name -> CuePalette.entries.firstOrNull { it.name == name } }
        ?: CuePalette.MINT,
    filenameTemplate = preferences[Keys.FILENAME_TEMPLATE]
        ?: AppSettings.DEFAULT_FILENAME_TEMPLATE,
    deleteSourceAfterSave = preferences[Keys.DELETE_SOURCE] ?: true,
    ocrIntervalMs = preferences[Keys.OCR_INTERVAL] ?: AppSettings.DEFAULT_OCR_INTERVAL_MS,
    httpTimeoutMs = preferences[Keys.HTTP_TIMEOUT] ?: AppSettings.DEFAULT_HTTP_TIMEOUT_MS,
    httpRetries = preferences[Keys.HTTP_RETRIES] ?: AppSettings.DEFAULT_HTTP_RETRIES,
    acrCloudHost = AcrCloudCredentials.orBuildDefault(
        stored = preferences[Keys.ACRCLOUD_HOST],
        buildDefault = BuildConfig.ACRCLOUD_HOST,
    ),
    acrCloudAccessKey = AcrCloudCredentials.orBuildDefault(
        stored = preferences[Keys.ACRCLOUD_ACCESS_KEY],
        buildDefault = BuildConfig.ACRCLOUD_ACCESS_KEY,
    ),
    acrCloudAccessSecret = AcrCloudCredentials.orBuildDefault(
        stored = preferences[Keys.ACRCLOUD_ACCESS_SECRET],
        buildDefault = BuildConfig.ACRCLOUD_ACCESS_SECRET,
    ),
    enrichmentEnabled = preferences[Keys.ENRICHMENT] ?: true,
)

/** Persisted names, kept next to the mapping so both stay in step. */
private object Keys {
    val SCAN_WINDOW = intPreferencesKey("scan_window_seconds")
    val QUALITY = stringPreferencesKey("default_quality")
    val OUTPUT_FOLDER = stringPreferencesKey("output_folder")
    val NETWORK = booleanPreferencesKey("network_lookups")
    val FINGERPRINT = booleanPreferencesKey("fingerprint_enabled")
    val ACOUSTID_KEY = stringPreferencesKey("acoustid_key")
    val LANGUAGE = stringPreferencesKey("language")
    val THEME = stringPreferencesKey("theme")
    val PALETTE = stringPreferencesKey("palette")
    val FILENAME_TEMPLATE = stringPreferencesKey("filename_template")
    val DELETE_SOURCE = booleanPreferencesKey("delete_source_after_save")
    val OCR_INTERVAL = longPreferencesKey("ocr_interval_ms")
    val HTTP_TIMEOUT = longPreferencesKey("http_timeout_ms")
    val HTTP_RETRIES = intPreferencesKey("http_retries")
    val ACRCLOUD_HOST = stringPreferencesKey("acrcloud_host")
    val ACRCLOUD_ACCESS_KEY = stringPreferencesKey("acrcloud_access_key")
    val ACRCLOUD_ACCESS_SECRET = stringPreferencesKey("acrcloud_access_secret")
    val ENRICHMENT = booleanPreferencesKey("enrichment_enabled")
}
