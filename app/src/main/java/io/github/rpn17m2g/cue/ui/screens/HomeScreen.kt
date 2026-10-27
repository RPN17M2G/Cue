package io.github.rpn17m2g.cue.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel

@Composable
fun HomeScreen(
    onStartReview: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenError: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { HomeViewModel(context.appContainer()) }
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.resolve(uri, onResolved = onStartReview, onError = onOpenError)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { picker.launch(INPUT_MIME_TYPES) },
                enabled = !resolving,
            ) {
                Text(stringResource(R.string.home_add_file))
            }
            AnimatedVisibility(
                visible = resolving,
                enter = fadeIn(tween(HOME_FADE_MILLIS)) +
                    expandVertically(tween(HOME_FADE_MILLIS)),
                exit = fadeOut(tween(HOME_FADE_MILLIS)) +
                    shrinkVertically(tween(HOME_FADE_MILLIS)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_preparing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = onOpenSettings, enabled = !resolving) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.home_settings),
                )
            }
        }
    }
}

private val INPUT_MIME_TYPES = arrayOf("video/*", "audio/*")

private const val HOME_FADE_MILLIS = 200
