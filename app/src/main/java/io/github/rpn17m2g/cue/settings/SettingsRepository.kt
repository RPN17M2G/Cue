package io.github.rpn17m2g.cue.settings

import io.github.rpn17m2g.cue.domain.model.QualityPreset
import kotlinx.coroutines.flow.Flow

enum class AppLanguage { SYSTEM, ENGLISH, HEBREW }

enum class AppTheme { SYSTEM, LIGHT, DARK }

/**
 * Hebrew text cannot be read by ML Kit's Latin recognizer, so the Hebrew Tesseract
 * pass is mandatory for a Hebrew UI and a fallback otherwise.
 */
fun AppLanguage.prefersHebrew(systemLanguage: String): Boolean = when (this) {
    AppLanguage.SYSTEM -> systemLanguage.lowercase().startsWith("he")
    AppLanguage.ENGLISH -> false
    AppLanguage.HEBREW -> true
}

data class AppSettings(
    val scanWindowSeconds: Int = DEFAULT_SCAN_WINDOW_SECONDS,
    val defaultQuality: QualityPreset = QualityPreset.V0,
    val outputFolder: String = DEFAULT_OUTPUT_FOLDER,
    val networkLookups: Boolean = true,
    val fingerprintEnabled: Boolean = true,
    val acoustIdKey: String = "",
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.SYSTEM,
    val filenameTemplate: String = DEFAULT_FILENAME_TEMPLATE,
    val ocrIntervalMs: Long = DEFAULT_OCR_INTERVAL_MS,
    val httpTimeoutMs: Long = DEFAULT_HTTP_TIMEOUT_MS,
    val httpRetries: Int = DEFAULT_HTTP_RETRIES,
) {
    val fingerprintAvailable: Boolean get() = fingerprintEnabled && acoustIdKey.isNotBlank()

    companion object {
        const val DEFAULT_SCAN_WINDOW_SECONDS = 15
        const val DEFAULT_OUTPUT_FOLDER = "Music/Cue"
        const val DEFAULT_FILENAME_TEMPLATE = "{artist} - {title}"
        const val DEFAULT_OCR_INTERVAL_MS = 2_000L
        const val DEFAULT_HTTP_TIMEOUT_MS = 10_000L
        const val DEFAULT_HTTP_RETRIES = 2

        val SCAN_WINDOW_RANGE = 5..120
        val OCR_INTERVAL_RANGE = 250L..10_000L
        val HTTP_TIMEOUT_RANGE = 1_000L..60_000L
        val HTTP_RETRIES_RANGE = 0..5
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
