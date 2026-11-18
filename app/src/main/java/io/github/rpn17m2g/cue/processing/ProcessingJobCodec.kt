package io.github.rpn17m2g.cue.processing

import android.content.Context
import android.content.Intent
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange
import io.github.rpn17m2g.cue.tags.TagField
import java.io.File

/**
 * Passes a job to the service.
 *
 * A cover can be a few hundred kilobytes, close to the Binder transaction
 * limit, so artwork bytes are staged in the cache directory and the intent
 * carries only the path. [cleanup] removes that file once the service has read
 * the job.
 */
internal object ProcessingJobCodec {

    fun write(context: Context, intent: Intent, job: ProcessingJob) {
        intent.putExtra(KEY_INPUT_ID, job.input.id)
        intent.putExtra(KEY_URI, job.input.uri)
        intent.putExtra(KEY_PATH, job.input.path)
        intent.putExtra(KEY_DISPLAY_NAME, job.input.displayName)
        intent.putExtra(KEY_MIME, job.input.mimeType)
        intent.putExtra(KEY_KIND, job.input.kind.name)
        intent.putExtra(KEY_DURATION, job.input.durationMs ?: ABSENT_LONG)
        intent.putExtra(KEY_CAN_EDIT, job.input.canEditInPlace)
        intent.putExtra(KEY_TITLE, job.draft.title)
        intent.putExtra(KEY_ARTIST, job.draft.artist)
        intent.putExtra(KEY_ALBUM, job.draft.album)
        intent.putExtra(KEY_ALBUM_ARTIST, job.draft.albumArtist)
        intent.putExtra(KEY_YEAR, job.draft.year)
        intent.putExtra(KEY_GENRE, job.draft.genre)
        intent.putExtra(KEY_TRACK, job.draft.trackNumber ?: ABSENT_INT)
        intent.putExtra(KEY_TRACK_TOTAL, job.draft.trackTotal ?: ABSENT_INT)
        intent.putExtra(KEY_LYRICS, job.draft.lyrics)
        intent.putExtra(KEY_SYNCED_LYRICS, job.draft.syncedLyrics)
        intent.putExtra(KEY_BPM, job.draft.bpm ?: ABSENT_INT)
        intent.putExtra(KEY_ISRC, job.draft.isrc)
        intent.putExtra(KEY_LABEL, job.draft.label)
        intent.putExtra(KEY_REPLAY_GAIN, job.draft.replayGainDb ?: ABSENT_DOUBLE)
        intent.putExtra(KEY_RECORDING_MBID, job.draft.recordingMbid)
        intent.putExtra(KEY_RELEASE_MBID, job.draft.releaseMbid)
        intent.putExtra(KEY_ARTIST_MBID, job.draft.artistMbid)
        job.draft.artwork?.let { artwork ->
            stageArtwork(context, artwork)?.let { staged ->
                intent.putExtra(KEY_ARTWORK_PATH, staged.absolutePath)
                intent.putExtra(KEY_ARTWORK_MIME, artwork.mimeType)
            }
        }
        intent.putExtra(KEY_TRIM_START, job.trim?.startMs ?: ABSENT_LONG)
        intent.putExtra(KEY_TRIM_END, job.trim?.endMs ?: ABSENT_LONG)
        intent.putExtra(KEY_QUALITY, job.quality.name)
        intent.putExtra(KEY_OUTPUT_FOLDER, job.outputFolder)
        intent.putExtra(KEY_TEMPLATE, job.filenameTemplate)
        intent.putExtra(KEY_MODE, job.mode.name)
        intent.putExtra(KEY_DELETE_SOURCE, job.deleteSource)
        intent.putStringArrayListExtra(
            KEY_CHANGED_FIELDS,
            ArrayList(job.changedFields.map(TagField::name)),
        )
    }

    fun read(intent: Intent): ProcessingJob? {
        val id = intent.getStringExtra(KEY_INPUT_ID) ?: return null
        val path = intent.getStringExtra(KEY_PATH) ?: return null
        val displayName = intent.getStringExtra(KEY_DISPLAY_NAME) ?: return null
        val kind = intent.getStringExtra(KEY_KIND)
            ?.let { name -> InputKind.entries.firstOrNull { it.name == name } }
            ?: return null
        val quality = intent.getStringExtra(KEY_QUALITY)
            ?.let { name -> QualityPreset.entries.firstOrNull { it.name == name } }
            ?: return null
        val trimStart = intent.getLongExtra(KEY_TRIM_START, ABSENT_LONG)
        val trimEnd = intent.getLongExtra(KEY_TRIM_END, ABSENT_LONG)
        val mode = intent.getStringExtra(KEY_MODE)
            ?.let { name -> ProcessingMode.entries.firstOrNull { it.name == name } }
            ?: ProcessingMode.CONVERT_AND_SAVE
        return ProcessingJob(
            input = ResolvedInput(
                id = id,
                uri = intent.getStringExtra(KEY_URI).orEmpty(),
                path = path,
                displayName = displayName,
                mimeType = intent.getStringExtra(KEY_MIME),
                kind = kind,
                durationMs = intent.getLongExtra(KEY_DURATION, ABSENT_LONG).takeIf { it != ABSENT_LONG },
                canEditInPlace = intent.getBooleanExtra(KEY_CAN_EDIT, false),
            ),
            draft = TagDraft(
                title = intent.getStringExtra(KEY_TITLE).orEmpty(),
                artist = intent.getStringExtra(KEY_ARTIST).orEmpty(),
                album = intent.getStringExtra(KEY_ALBUM).orEmpty(),
                albumArtist = intent.getStringExtra(KEY_ALBUM_ARTIST).orEmpty(),
                year = intent.getStringExtra(KEY_YEAR),
                genre = intent.getStringExtra(KEY_GENRE),
                trackNumber = intent.getIntExtra(KEY_TRACK, ABSENT_INT).takeIf { it != ABSENT_INT },
                trackTotal = intent.getIntExtra(KEY_TRACK_TOTAL, ABSENT_INT).takeIf { it != ABSENT_INT },
                artwork = readArtwork(intent),
                lyrics = intent.getStringExtra(KEY_LYRICS),
                syncedLyrics = intent.getStringExtra(KEY_SYNCED_LYRICS),
                bpm = intent.getIntExtra(KEY_BPM, ABSENT_INT).takeIf { it != ABSENT_INT },
                isrc = intent.getStringExtra(KEY_ISRC),
                label = intent.getStringExtra(KEY_LABEL),
                replayGainDb = intent.getDoubleExtra(KEY_REPLAY_GAIN, ABSENT_DOUBLE)
                    .takeIf { !it.isNaN() },
                recordingMbid = intent.getStringExtra(KEY_RECORDING_MBID),
                releaseMbid = intent.getStringExtra(KEY_RELEASE_MBID),
                artistMbid = intent.getStringExtra(KEY_ARTIST_MBID),
            ),
            trim = if (trimStart != ABSENT_LONG && trimEnd != ABSENT_LONG && trimEnd > trimStart) {
                TrimRange(trimStart, trimEnd)
            } else {
                null
            },
            quality = quality,
            outputFolder = intent.getStringExtra(KEY_OUTPUT_FOLDER).orEmpty(),
            filenameTemplate = intent.getStringExtra(KEY_TEMPLATE).orEmpty(),
            mode = mode,
            deleteSource = intent.getBooleanExtra(KEY_DELETE_SOURCE, false),
            changedFields = intent.getStringArrayListExtra(KEY_CHANGED_FIELDS)
                .orEmpty()
                .mapNotNull { name -> TagField.entries.firstOrNull { it.name == name } }
                .toSet(),
        )
    }

    /** Removes the staged cover once the service no longer needs the job. */
    fun cleanup(intent: Intent?) {
        intent?.getStringExtra(KEY_ARTWORK_PATH)?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    private fun stageArtwork(context: Context, artwork: Artwork): File? = runCatching {
        val directory = File(context.cacheDir, ARTWORK_DIRECTORY).apply { mkdirs() }
        File(directory, "artwork-${System.nanoTime()}.bin").apply {
            writeBytes(artwork.bytes)
        }
    }.getOrNull()

    private fun readArtwork(intent: Intent): Artwork? {
        val path = intent.getStringExtra(KEY_ARTWORK_PATH) ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            Artwork(
                bytes = file.readBytes(),
                mimeType = intent.getStringExtra(KEY_ARTWORK_MIME) ?: DEFAULT_ARTWORK_MIME,
            )
        }.getOrNull()
    }

    private const val ARTWORK_DIRECTORY = "processing-jobs"
    private const val DEFAULT_ARTWORK_MIME = "image/jpeg"

    private const val ABSENT_LONG = -1L
    private const val ABSENT_INT = -1
    private const val ABSENT_DOUBLE = Double.NaN

    private const val KEY_INPUT_ID = "input_id"
    private const val KEY_URI = "input_uri"
    private const val KEY_PATH = "input_path"
    private const val KEY_DISPLAY_NAME = "input_name"
    private const val KEY_MIME = "input_mime"
    private const val KEY_KIND = "input_kind"
    private const val KEY_DURATION = "input_duration"
    private const val KEY_CAN_EDIT = "input_can_edit"
    private const val KEY_TITLE = "tag_title"
    private const val KEY_ARTIST = "tag_artist"
    private const val KEY_ALBUM = "tag_album"
    private const val KEY_ALBUM_ARTIST = "tag_album_artist"
    private const val KEY_YEAR = "tag_year"
    private const val KEY_GENRE = "tag_genre"
    private const val KEY_TRACK = "tag_track"
    private const val KEY_TRACK_TOTAL = "tag_track_total"
    private const val KEY_LYRICS = "tag_lyrics"
    private const val KEY_SYNCED_LYRICS = "tag_synced_lyrics"
    private const val KEY_BPM = "tag_bpm"
    private const val KEY_ISRC = "tag_isrc"
    private const val KEY_LABEL = "tag_label"
    private const val KEY_REPLAY_GAIN = "tag_replay_gain"
    private const val KEY_RECORDING_MBID = "tag_recording_mbid"
    private const val KEY_RELEASE_MBID = "tag_release_mbid"
    private const val KEY_ARTIST_MBID = "tag_artist_mbid"
    private const val KEY_ARTWORK_PATH = "tag_artwork_path"
    private const val KEY_ARTWORK_MIME = "tag_artwork_mime"
    private const val KEY_TRIM_START = "trim_start"
    private const val KEY_TRIM_END = "trim_end"
    private const val KEY_QUALITY = "quality"
    private const val KEY_OUTPUT_FOLDER = "output_folder"
    private const val KEY_TEMPLATE = "filename_template"
    private const val KEY_MODE = "processing_mode"
    private const val KEY_DELETE_SOURCE = "delete_source"
    private const val KEY_CHANGED_FIELDS = "changed_fields"
}
