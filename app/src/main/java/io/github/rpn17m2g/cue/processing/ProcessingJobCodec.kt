package io.github.rpn17m2g.cue.processing

import android.content.Intent
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.TrimRange

/** Passes a job to the service without serializing artwork bytes. */
internal object ProcessingJobCodec {

    fun write(intent: Intent, job: ProcessingJob) {
        intent.putExtra(KEY_INPUT_ID, job.input.id)
        intent.putExtra(KEY_URI, job.input.uri)
        intent.putExtra(KEY_PATH, job.input.path)
        intent.putExtra(KEY_DISPLAY_NAME, job.input.displayName)
        intent.putExtra(KEY_MIME, job.input.mimeType)
        intent.putExtra(KEY_KIND, job.input.kind.name)
        intent.putExtra(KEY_DURATION, job.input.durationMs ?: ABSENT_LONG)
        intent.putExtra(KEY_TITLE, job.draft.title)
        intent.putExtra(KEY_ARTIST, job.draft.artist)
        intent.putExtra(KEY_ALBUM, job.draft.album)
        intent.putExtra(KEY_ALBUM_ARTIST, job.draft.albumArtist)
        intent.putExtra(KEY_YEAR, job.draft.year)
        intent.putExtra(KEY_GENRE, job.draft.genre)
        intent.putExtra(KEY_TRACK, job.draft.trackNumber ?: ABSENT_INT)
        intent.putExtra(KEY_TRACK_TOTAL, job.draft.trackTotal ?: ABSENT_INT)
        intent.putExtra(KEY_TRIM_START, job.trim?.startMs ?: ABSENT_LONG)
        intent.putExtra(KEY_TRIM_END, job.trim?.endMs ?: ABSENT_LONG)
        intent.putExtra(KEY_QUALITY, job.quality.name)
        intent.putExtra(KEY_OUTPUT_FOLDER, job.outputFolder)
        intent.putExtra(KEY_TEMPLATE, job.filenameTemplate)
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
        return ProcessingJob(
            input = ResolvedInput(
                id = id,
                uri = intent.getStringExtra(KEY_URI).orEmpty(),
                path = path,
                displayName = displayName,
                mimeType = intent.getStringExtra(KEY_MIME),
                kind = kind,
                durationMs = intent.getLongExtra(KEY_DURATION, ABSENT_LONG).takeIf { it != ABSENT_LONG },
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
            ),
            trim = if (trimStart != ABSENT_LONG && trimEnd != ABSENT_LONG && trimEnd > trimStart) {
                TrimRange(trimStart, trimEnd)
            } else {
                null
            },
            quality = quality,
            outputFolder = intent.getStringExtra(KEY_OUTPUT_FOLDER).orEmpty(),
            filenameTemplate = intent.getStringExtra(KEY_TEMPLATE).orEmpty(),
        )
    }

    private const val ABSENT_LONG = -1L
    private const val ABSENT_INT = -1

    private const val KEY_INPUT_ID = "input_id"
    private const val KEY_URI = "input_uri"
    private const val KEY_PATH = "input_path"
    private const val KEY_DISPLAY_NAME = "input_name"
    private const val KEY_MIME = "input_mime"
    private const val KEY_KIND = "input_kind"
    private const val KEY_DURATION = "input_duration"
    private const val KEY_TITLE = "tag_title"
    private const val KEY_ARTIST = "tag_artist"
    private const val KEY_ALBUM = "tag_album"
    private const val KEY_ALBUM_ARTIST = "tag_album_artist"
    private const val KEY_YEAR = "tag_year"
    private const val KEY_GENRE = "tag_genre"
    private const val KEY_TRACK = "tag_track"
    private const val KEY_TRACK_TOTAL = "tag_track_total"
    private const val KEY_TRIM_START = "trim_start"
    private const val KEY_TRIM_END = "trim_end"
    private const val KEY_QUALITY = "quality"
    private const val KEY_OUTPUT_FOLDER = "output_folder"
    private const val KEY_TEMPLATE = "filename_template"
}
