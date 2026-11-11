package io.github.rpn17m2g.cue.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rpn17m2g.cue.data.metadata.FingerprintInput
import io.github.rpn17m2g.cue.data.metadata.LookupError
import io.github.rpn17m2g.cue.data.metadata.LookupResult
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.settings.AcoustIdKey
import io.github.rpn17m2g.cue.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of the explicit "check key" action in Settings. */
enum class KeyCheck { IDLE, CHECKING, VALID, REJECTED, UNAVAILABLE, ERROR }

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _keyCheck = MutableStateFlow(KeyCheck.IDLE)
    val keyCheck: StateFlow<KeyCheck> = _keyCheck.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    /** A different key invalidates the previous check result. */
    fun clearKeyCheck() {
        _keyCheck.value = KeyCheck.IDLE
    }

    /**
     * Sends one lookup with a known fingerprint. A valid application key
     * answers `status: ok` even when the fingerprint matches nothing; a
     * rejected key comes back as error code 4. This is the only reliable way
     * to tell a wrong key from a song AcoustID simply does not know, which is
     * exactly the confusion that makes fingerprinting look broken.
     */
    fun checkAcoustIdKey() {
        if (_keyCheck.value == KeyCheck.CHECKING) return
        viewModelScope.launch {
            val current = container.settingsRepository.settings.first()
            if (!current.networkLookups) {
                _keyCheck.value = KeyCheck.UNAVAILABLE
                return@launch
            }
            if (!AcoustIdKey.isPlausible(current.acoustIdKey)) {
                _keyCheck.value = KeyCheck.REJECTED
                return@launch
            }
            _keyCheck.value = KeyCheck.CHECKING
            val result = try {
                container.metadataClients().acoustId.lookup(
                    FingerprintInput(PROBE_FINGERPRINT, PROBE_DURATION_SECONDS),
                    current.acoustIdKey,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            _keyCheck.value = when (result) {
                is LookupResult.Success -> KeyCheck.VALID
                is LookupResult.Failure -> when (result.error) {
                    LookupError.RejectedKey, LookupError.MissingKey -> KeyCheck.REJECTED
                    else -> KeyCheck.ERROR
                }
                null -> KeyCheck.ERROR
            }
        }
    }

    private companion object {
        /**
         * A real, valid fingerprint of silence. AcoustID validates the client
         * key before it looks at the fingerprint, so a rejected key still
         * returns error code 4, while a working key answers `status: ok` with
         * an empty result list.
         */
        const val PROBE_FINGERPRINT =
            "AQAAfEmUaEmSTUwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val PROBE_DURATION_SECONDS = 18
    }
}
