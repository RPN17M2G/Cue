package io.github.rpn17m2g.cue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Guards the Hebrew resource qualifier. Android resolves a `he` request through
 * the legacy `iw` language code, so the translations must live in `values-iw`;
 * with `values-he` they compile into the APK but never match, and every string
 * silently falls back to English while the layout still flips to RTL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HebrewResourcesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun hebrew(id: Int): String {
        RuntimeEnvironment.setQualifiers("he")
        return context.getString(id)
    }

    private fun english(id: Int): String {
        RuntimeEnvironment.setQualifiers("en-rGB")
        return context.getString(id)
    }

    @Test
    fun `hebrew locale resolves translated strings`() {
        assertEquals("הגדרות", hebrew(R.string.settings_title))
        assertEquals("הוספת קובץ", hebrew(R.string.home_add_file))
    }

    @Test
    fun `hebrew strings are not english fallbacks`() {
        val samples = listOf(
            R.string.home_subtitle,
            R.string.settings_language,
            R.string.settings_theme,
            R.string.home_settings,
            R.string.settings_after_processing,
            R.string.settings_delete_source,
            R.string.settings_delete_source_note,
            R.string.result_delete_title,
            R.string.result_delete_body,
            R.string.result_delete_confirm,
            R.string.result_delete_cancel,
            R.string.result_source_deleted,
            R.string.result_source_kept,
            R.string.result_delete_failed,
        )

        samples.forEach { id ->
            val hebrew = hebrew(id)
            val english = english(id)
            assertTrue("expected Hebrew script, got '$hebrew'", hebrew.any { it in '\u0590'..'\u05FF' })
            assertTrue("expected a translation, got the English value '$hebrew'", hebrew != english)
        }
    }

    @Test
    fun `english locale still resolves english`() {
        assertEquals("Settings", english(R.string.settings_title))
        assertEquals("Add file", english(R.string.home_add_file))
    }
}
