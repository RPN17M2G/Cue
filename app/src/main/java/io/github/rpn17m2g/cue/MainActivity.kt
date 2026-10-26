package io.github.rpn17m2g.cue

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.rpn17m2g.cue.data.input.InputIntentParser
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.ui.navigation.CueNavHost
import io.github.rpn17m2g.cue.ui.navigation.Routes
import io.github.rpn17m2g.cue.ui.theme.CueTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val container: AppContainer
        get() = (application as CueApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            CueTheme {
                CueNavHost()
            }
        }
        handleIntent(intent)
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
