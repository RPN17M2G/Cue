package io.github.rpn17m2g.cue.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.cueViewModel

@Composable
fun ResultScreen(
    onProcessAnother: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { ResultViewModel(context.appContainer()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = (state as? ProcessingState.Done)?.song

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (song == null) {
                Text(
                    text = stringResource(R.string.result_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = song.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.result_saved_to, song.relativePath),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { play(context, song) }) {
                        Text(stringResource(R.string.result_play))
                    }
                    OutlinedButton(onClick = {
                        openWith(context, song, context.getString(R.string.result_open_with))
                    }) {
                        Text(stringResource(R.string.result_open_with))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        share(context, song, context.getString(R.string.result_share_chooser))
                    }) {
                        Text(stringResource(R.string.result_share))
                    }
                    OutlinedButton(onClick = { showInFolder(context, song) }) {
                        Text(stringResource(R.string.result_show_folder))
                    }
                }
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.result_edit_tags))
                }
                Text(
                    text = stringResource(R.string.result_edit_tags_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    viewModel.processAnother()
                    onProcessAnother()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.result_process_another))
            }
        }
    }
}

private fun play(context: android.content.Context, song: SavedSong) {
    runCatching { context.startActivity(viewIntent(song)) }
}

private fun openWith(context: Context, song: SavedSong, chooserTitle: String) {
    runCatching {
        context.startActivity(Intent.createChooser(viewIntent(song), chooserTitle))
    }
}

private fun showInFolder(context: Context, song: SavedSong) {
    val folder = song.relativePath.trimEnd('/')
    val documentUri = DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        "primary:$folder",
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, R.string.result_show_folder_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: android.content.Context, song: SavedSong, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("audio/mpeg")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(song.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

private fun viewIntent(song: SavedSong): Intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(Uri.parse(song.uri), "audio/mpeg")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
