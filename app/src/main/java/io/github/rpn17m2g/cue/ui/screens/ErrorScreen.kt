package io.github.rpn17m2g.cue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel

@Composable
fun ErrorScreen(
    onRetryStarted: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { ErrorViewModel(context.appContainer()) }
    val report by viewModel.report.collectAsStateWithLifecycle()
    val processing by viewModel.processingState.collectAsStateWithLifecycle()
    val failed = processing as? ProcessingState.Failed
    val fallback = stringResource(R.string.error_processing_failed)
    val reason = report?.reason ?: failed?.reason ?: fallback
    val details = report?.details ?: failed?.details
    var expanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.error_details_hide else R.string.error_details,
                    ),
                )
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        text = details ?: stringResource(R.string.error_details_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    val inputId = viewModel.retry()
                    if (inputId != null) {
                        onRetryStarted(inputId)
                    } else {
                        viewModel.clearReport()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.error_retry))
            }
            OutlinedButton(
                onClick = {
                    viewModel.clearReport()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.error_back))
            }
        }
    }
}
