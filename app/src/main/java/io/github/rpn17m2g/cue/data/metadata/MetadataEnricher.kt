package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.metadata.TagField
import io.github.rpn17m2g.cue.domain.metadata.TagMergeResult
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.TagDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Lyrics lookup, narrowed so the enricher can be tested without HTTP. */
fun interface LyricsSource {
    suspend fun fetch(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
    ): Lyrics?
}

/** BPM / ReplayGain / ISRC lookup, narrowed for the same reason. */
fun interface BpmSource {
    suspend fun lookup(title: String, artist: String): DeezerTrack?
}

/**
 * Fills the optional tags a confident match does not provide. Lyrics and the
 * Deezer fields are independent, so they run in parallel; a failure in either
 * is simply "nothing found" and never blocks the save.
 *
 * Enrichment only fills blanks. A value the user edited, an embedded tag, or a
 * provider result already in the draft always wins.
 */
class MetadataEnricher(
    private val lyrics: LyricsSource,
    private val bpm: BpmSource,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) {

    suspend fun enrich(
        draft: TagDraft,
        durationSeconds: Int?,
    ): TagMergeResult = coroutineScope {
        if (!networkGate.isNetworkEnabled()) return@coroutineScope unchanged(draft)
        if (draft.title.isBlank() || draft.artist.isBlank()) return@coroutineScope unchanged(draft)

        val needLyrics = draft.lyrics.isNullOrBlank()
        val needBpm = draft.bpm == null ||
            draft.replayGainDb == null ||
            draft.isrc.isNullOrBlank()
        if (!needLyrics && !needBpm) return@coroutineScope unchanged(draft)

        val lyricsJob = if (needLyrics) {
            async {
                safely {
                    lyrics.fetch(
                        title = draft.title,
                        artist = draft.artist,
                        album = draft.album.takeIf { it.isNotBlank() },
                        durationSeconds = durationSeconds?.takeIf { it > 0 },
                    )
                }
            }
        } else {
            null
        }
        val bpmJob = if (needBpm) {
            async { safely { bpm.lookup(draft.title, draft.artist) } }
        } else {
            null
        }

        var tag = draft
        val sources = linkedMapOf<TagField, IdentificationSource>()

        lyricsJob?.await()?.let { found ->
            if (tag.lyrics.isNullOrBlank()) {
                // Keep both halves: the plain text for USLT and the LRC for SYLT.
                tag = tag.copy(lyrics = found.text, syncedLyrics = found.lrc)
                sources[TagField.LYRICS] = IdentificationSource.LRCLIB
            }
        }
        bpmJob?.await()?.let { found ->
            if (tag.bpm == null && found.bpm != null) {
                tag = tag.copy(bpm = found.bpm)
                sources[TagField.BPM] = IdentificationSource.DEEZER
            }
            if (tag.replayGainDb == null && found.replayGainDb != null) {
                tag = tag.copy(replayGainDb = found.replayGainDb)
                sources[TagField.REPLAY_GAIN] = IdentificationSource.DEEZER
            }
            if (tag.isrc.isNullOrBlank() && !found.isrc.isNullOrBlank()) {
                tag = tag.copy(isrc = found.isrc)
                sources[TagField.ISRC] = IdentificationSource.DEEZER
            }
        }
        TagMergeResult(tag, sources)
    }

    private fun unchanged(draft: TagDraft) = TagMergeResult(draft, emptyMap())

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
