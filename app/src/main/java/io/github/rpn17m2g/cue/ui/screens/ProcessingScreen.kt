package io.github.rpn17m2g.cue.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.processing.ProcessingStage
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.theme.LocalCueTokens
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
    val tokens = LocalCueTokens.current

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
    val stages = if (viewModel.inPlace) IN_PLACE_STAGES else STAGES
    val currentIndex = currentStage?.let(stages::indexOf) ?: -1
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_MILLIS),
        label = "processing progress",
    )
    // Canvas drawing is not composable, so resolve the theme colours first.
    val ringTrack = MaterialTheme.colorScheme.surfaceVariant
    val ringBrush = Brush.sweepGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            tokens.glow,
            MaterialTheme.colorScheme.primary,
        ),
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = stringResource(R.string.processing_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Box(
                modifier = Modifier.size(RING_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .background(tokens.glow.copy(alpha = 0.10f), CircleShape),
                )
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = RING_STROKE.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = ringTrack,
                        startAngle = START_ANGLE,
                        sweepAngle = FULL_TURN,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    if (animatedProgress > 0f) {
                        drawArc(
                            brush = ringBrush,
                            startAngle = START_ANGLE,
                            sweepAngle = FULL_TURN * animatedProgress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.processing_percent,
                            (animatedProgress * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    currentStage?.let { stage ->
                        Text(
                            text = stringResource(stage.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                stages.forEachIndexed { index, stage ->
                    StageRow(
                        label = stringResource(stage.labelRes()),
                        icon = when {
                            currentIndex > index -> Icons.Default.CheckCircle
                            currentIndex == index -> Icons.Default.RadioButtonChecked
                            else -> Icons.Default.RadioButtonUnchecked
                        },
                        isActive = currentIndex == index,
                        isDone = currentIndex > index,
                    )
                }
            }
            TextButton(
                onClick = {
                    viewModel.cancel()
                    onCancelled()
                },
            ) {
                Text(
                    text = stringResource(R.string.processing_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    isDone: Boolean,
) {
    val tokens = LocalCueTokens.current
    val iconTint by animateColorAsState(
        targetValue = when {
            isDone -> tokens.success
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = STAGE_MILLIS),
        label = "stage icon tint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = STAGE_MILLIS),
        label = "stage label color",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                fadeIn(tween(durationMillis = STAGE_MILLIS)) togetherWith
                    fadeOut(tween(durationMillis = STAGE_MILLIS))
            },
            label = "stage icon",
        ) { current ->
            Icon(
                imageVector = current,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
    }
}

private const val PROGRESS_MILLIS = 350
private const val STAGE_MILLIS = 200
private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
private val RING_SIZE = 220.dp
private val RING_STROKE = 16.dp

private val STAGES = listOf(
    ProcessingStage.Preparing,
    ProcessingStage.Converting,
    ProcessingStage.Tagging,
    ProcessingStage.Saving,
)

private val IN_PLACE_STAGES = listOf(
    ProcessingStage.Preparing,
    ProcessingStage.Tagging,
    ProcessingStage.Saving,
)

private fun ProcessingStage.labelRes(): Int = when (this) {
    ProcessingStage.Preparing -> R.string.processing_stage_prepare
    ProcessingStage.Converting -> R.string.processing_stage_convert
    ProcessingStage.Tagging -> R.string.processing_stage_tag
    ProcessingStage.Saving -> R.string.processing_stage_save
}
