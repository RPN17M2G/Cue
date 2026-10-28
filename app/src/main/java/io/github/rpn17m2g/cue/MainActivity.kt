package io.github.rpn17m2g.cue

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.rpn17m2g.cue.data.input.InputIntentParser
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.settings.AppLanguage
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.localeTag
import io.github.rpn17m2g.cue.ui.navigation.CueNavHost
import io.github.rpn17m2g.cue.ui.navigation.Routes
import io.github.rpn17m2g.cue.ui.theme.CueTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val container: AppContainer
        get() = (application as CueApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        applyLanguageChanges()
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            CueTheme(theme = settings.theme) {
                CueNavHost()
            }
        }
        if (savedInstanceState == null) {
            // Only the launching intent describes where to go. A recreation
            // (language change, theme, rotation) keeps the nav state instead of
            // replaying the original VIEW intent.
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (InputIntentParser.extractOpenTarget(intent)) {
            InputIntentParser.OPEN_RESULT -> {
                container.navigationRequests.navigate(Routes.RESULT)
                return
            }
            InputIntentParser.OPEN_ERROR -> {
                container.navigationRequests.navigate(Routes.ERROR)
                return
            }
            InputIntentParser.OPEN_PROCESSING -> {
                val inputId = intent?.getStringExtra(InputIntentParser.EXTRA_OPEN_INPUT_ID)
                if (!inputId.isNullOrBlank()) {
                    container.navigationRequests.navigate(Routes.processing(inputId))
                    return
                }
            }
        }
        val uri = InputIntentParser.extractUri(intent) ?: return
        if (container.processingSession.isRunning) {
            container.errorReporter.report(getString(R.string.error_busy))
            container.navigationRequests.navigate(Routes.ERROR)
            return
        }
        lifecycleScope.launch {
            try {
                container.sessionRepository.current.value?.let { previous ->
                    container.inputResolver.cleanup(previous.input)
                }
                container.sessionRepository.clear()
                container.inputResolver.releasePersistedPermissions()
                val resolved = container.inputResolver.resolve(uri)
                val session = container.sessionRepository.create(resolved)
                container.navigationRequests.navigate(Routes.review(session.id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                container.errorReporter.report(getString(R.string.error_input_failed), e.message)
                container.navigationRequests.navigate(Routes.ERROR)
            }
        }
    }

    /**
     * AppCompat stores the selected tag, applies it before the activity is
     * recreated, and recreates on a change so RTL and translated resources load
     * from the start of the next screen pass.
     */
    private fun applyLanguageChanges() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settingsRepository.settings.collect { settings ->
                    applyLanguage(settings.language)
                }
            }
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val desired = language.localeTag()
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
