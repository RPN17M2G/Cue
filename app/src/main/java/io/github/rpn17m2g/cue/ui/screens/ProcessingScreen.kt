package io.github.rpn17m2g.cue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.processing.ProcessingStage
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(
    onFinished: () -> Unit,
    onError: () -> Unit,
    onCancelled: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { ProcessingViewModel(context.appContainer()) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            is ProcessingState.Done -> onFinished()
            is ProcessingState.Failed -> onError()
            else -> Unit
        }
    }

    val running = state as? ProcessingState.Running
    val currentStage = running?.stage
    val progress = running?.progress ?: 0f
    val currentIndex = currentStage?.let(STAGES::indexOf) ?: -1

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.processing_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.processing_percent,
                    (progress * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                STAGES.forEachIndexed { index, stage ->
                    StageRow(
                        label = stringResource(stage.labelRes()),
                        icon = when {
                            currentIndex > index -> Icons.Default.CheckCircle
                            currentIndex == index -> Icons.Default.RadioButtonChecked
                            else -> Icons.Default.RadioButtonUnchecked
                        },
                        isActive = currentIndex == index,
                    )
                }
            }
            Button(onClick = {
                viewModel.cancel()
                onCancelled()
            }) {
                Text(stringResource(R.string.processing_cancel))
            }
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private val STAGES = listOf(
    ProcessingStage.Preparing,
    ProcessingStage.Converting,
    ProcessingStage.Tagging,
    ProcessingStage.Saving,
)

private fun ProcessingStage.labelRes(): Int = when (this) {
    ProcessingStage.Preparing -> R.string.processing_stage_prepare
    ProcessingStage.Converting -> R.string.processing_stage_convert
    ProcessingStage.Tagging -> R.string.processing_stage_tag
    ProcessingStage.Saving -> R.string.processing_stage_save
}
