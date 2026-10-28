package io.github.rpn17m2g.cue.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rpn17m2g.cue.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The AcoustID key has two sources: the build-time default in
 * `local.properties` and whatever the user saved in Settings. An early build
 * wrote the default back into the store, so a stale `""` entry has to mean
 * "unset" rather than "no key", or fingerprint lookup stays dead forever.
 *
 * The storage key is spelled out on purpose: renaming it would silently orphan
 * every saved setting, so the test should fail if it changes.
 */
class DataStoreSettingsRepositoryTest {

    private val acoustIdKey = stringPreferencesKey("acoustid_key")

    @Test
    fun `an absent acoustid key falls back to the build-time default`() {
        val settings = mapPreferences(mutablePreferencesOf())

        assertEquals(BuildConfig.ACOUSTID_API_KEY, settings.acoustIdKey)
    }

    @Test
    fun `a blank acoustid key falls back to the build-time default`() {
        val settings = mapPreferences(mutablePreferencesOf(acoustIdKey to ""))

        assertEquals(BuildConfig.ACOUSTID_API_KEY, settings.acoustIdKey)
    }

    @Test
    fun `a saved acoustid key wins over the build-time default`() {
        val settings = mapPreferences(mutablePreferencesOf(acoustIdKey to "saved-key"))

        assertEquals("saved-key", settings.acoustIdKey)
    }
}
