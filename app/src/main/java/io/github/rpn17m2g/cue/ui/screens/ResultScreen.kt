package io.github.rpn17m2g.cue.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.data.output.SourceOutcome
import io.github.rpn17m2g.cue.domain.model.TrimClock
import io.github.rpn17m2g.cue.processing.ProcessingState
import io.github.rpn17m2g.cue.ui.cueViewModel
import io.github.rpn17m2g.cue.ui.navigation.appContainer
import io.github.rpn17m2g.cue.ui.theme.LocalCueTokens

@Composable
fun ResultScreen(
    onProcessAnother: () -> Unit,
    onEditTags: (String) -> Unit,
    onEditError: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = cueViewModel { ResultViewModel(context.appContainer()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val song = (state as? ProcessingState.Done)?.song

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.completeSystemDelete(result.resultCode == Activity.RESULT_OK)
    }

    // A pending source is confirmed here first; MediaStore then adds the OS
    // dialog. Keeping the flag saveable stops rotation from asking twice.
    var showDeleteDialog by rememberSaveable(song?.uri) {
        mutableStateOf(song?.sourceOutcome == SourceOutcome.PENDING)
    }

    if (song != null && song.sourceOutcome == SourceOutcome.PENDING && showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                viewModel.keepSource()
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.result_delete_title)) },
            text = { Text(stringResource(R.string.result_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val request = viewModel.deleteRequest()
                        if (request != null) {
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(request).build(),
                            )
                        } else {
                            viewModel.deleteWithoutSystemDialog { deleted ->
                                if (!deleted) {
                                    Toast.makeText(
                                        context,
                                        R.string.result_delete_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.result_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.keepSource()
                    },
                ) {
                    Text(stringResource(R.string.result_delete_cancel))
                }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(
                    if (song?.inPlaceEdit == true) {
                        R.string.result_title_updated
                    } else {
                        R.string.result_title
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (song == null) {
                Text(
                    text = stringResource(R.string.result_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                SavedSongHeader(song)
                SavedActions(
                    song = song,
                    editing = editing,
                    onEditTags = {
                        viewModel.editTags(song, onReady = onEditTags, onError = onEditError)
                    },
                )
                SourceOutcomeLine(song)
            }
            Button(
                onClick = {
                    viewModel.processAnother()
                    onProcessAnother()
                },
                shape = LocalCueTokens.current.buttonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.result_process_another))
            }
        }
    }
}

@Composable
private fun SavedSongHeader(song: SavedSong) {
    val tokens = LocalCueTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(song.artwork) {
            song.artwork?.let { decodeArtwork(it.bytes, maxWidth = 512) }
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(tokens.cardShape)
                .background(tokens.heroBrush),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.review_field_artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = tokens.heroInk.copy(alpha = 0.85f),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = song.title.ifBlank {
                    song.displayName.substringBeforeLast('.')
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artist.isNotBlank()) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                song.durationMs?.let { duration ->
                    Text(
                        text = TrimClock.format(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!song.inPlaceEdit && song.relativePath.isNotBlank()) {
                    PathChip(song.relativePath.trimEnd('/'))
                }
            }
        }
    }
}

@Composable
private fun PathChip(path: String) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SavedActions(
    song: SavedSong,
    editing: Boolean,
    onEditTags: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalCueTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { play(context, song) },
                shape = tokens.buttonShape,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.result_play))
            }
            OutlinedButton(
                onClick = {
                    share(context, song, context.getString(R.string.result_share_chooser))
                },
                shape = tokens.buttonShape,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.result_share))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    openWith(context, song, context.getString(R.string.result_open_with))
                },
                shape = tokens.buttonShape,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.result_open_with))
            }
            if (song.relativePath.isNotBlank()) {
                OutlinedButton(
                    onClick = { showInFolder(context, song) },
                    shape = tokens.buttonShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.result_show_folder))
                }
            }
        }
        OutlinedButton(
            onClick = onEditTags,
            enabled = !editing,
            shape = tokens.buttonShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (editing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.result_edit_tags))
            }
        }
    }
}

@Composable
private fun SourceOutcomeLine(song: SavedSong) {
    val deleted = song.sourceOutcome == SourceOutcome.DELETED
    val kept = song.sourceOutcome == SourceOutcome.KEPT
    if (!deleted && !kept) return
    val tokens = LocalCueTokens.current
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (deleted) {
            tokens.success.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (deleted) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (deleted) tokens.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(
                    if (deleted) R.string.result_source_deleted else R.string.result_source_kept,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (deleted) tokens.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun play(context: Context, song: SavedSong) {
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

private fun share(context: Context, song: SavedSong, chooserTitle: String) {
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
