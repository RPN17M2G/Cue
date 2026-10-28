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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings, enabled = !resolving) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = { picker.launch(INPUT_MIME_TYPES) },
                enabled = !resolving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_add_file),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            AnimatedVisibility(
                visible = resolving,
                enter = fadeIn(tween(HOME_FADE_MILLIS)) +
                    expandVertically(tween(HOME_FADE_MILLIS)),
                exit = fadeOut(tween(HOME_FADE_MILLIS)) +
                    shrinkVertically(tween(HOME_FADE_MILLIS)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_preparing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private val INPUT_MIME_TYPES = arrayOf("video/*", "audio/*")

private const val HOME_FADE_MILLIS = 200
