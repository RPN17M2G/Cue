package io.github.rpn17m2g.cue.tags

import com.mpatric.mp3agic.AbstractID3v2Tag
import com.mpatric.mp3agic.EncodedText
import com.mpatric.mp3agic.ID3v1
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v23Tag
import com.mpatric.mp3agic.ID3v2Frame
import com.mpatric.mp3agic.ID3v2FrameSet
import com.mpatric.mp3agic.ID3v2PictureFrameData
import com.mpatric.mp3agic.ID3v2TextFrameData
import com.mpatric.mp3agic.Mp3File
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads and rewrites ID3 tags with mp3agic. The audio stream is never re-encoded; a
 * write copies the file once because mp3agic cannot save over its input.
 */
class Mp3TagStore {

    fun read(file: File): TagDraft? {
        val mp3 = load(file)
        mp3.id3v2Tag?.let { return readV2(it) }
        mp3.id3v1Tag?.let { return readV1(it) }
        return null
    }

    fun writeInPlace(file: File, tag: TagDraft, fields: Set<TagField>? = null) {
        val targets = fields ?: TagField.entries.toSet()
        try {
            val mp3 = load(file)
            val id3 = ensureV23(mp3)

            if (TagField.TITLE in targets) {
                writeText(id3, AbstractID3v2Tag.ID_TITLE, tag.title)
            }
            if (TagField.ARTIST in targets) {
                writeText(id3, AbstractID3v2Tag.ID_ARTIST, tag.artist)
            }
            if (TagField.ALBUM in targets) {
                writeText(id3, AbstractID3v2Tag.ID_ALBUM, tag.album)
            }
            if (TagField.ALBUM_ARTIST in targets) {
                writeText(id3, AbstractID3v2Tag.ID_ALBUM_ARTIST, tag.albumArtist)
            }
            if (TagField.YEAR in targets) {
                writeText(id3, AbstractID3v2Tag.ID_YEAR, tag.year)
            }
            if (TagField.GENRE in targets) {
                writeText(id3, AbstractID3v2Tag.ID_GENRE, tag.genre)
            }
            if (TagField.TRACK_NUMBER in targets) {
                writeText(id3, AbstractID3v2Tag.ID_TRACK, tag.trackNumber?.toString())
            }
            if (TagField.ARTWORK in targets) {
                writeArtwork(id3, tag.artwork)
            }

            if (id3.frameSets.isEmpty()) {
                id3.padding = true
            }
            invalidateCachedLength(id3)

            mp3.id3v2Tag = id3
            saveAtomically(mp3, file)
        } catch (e: Exception) {
            if (e is TagWriteException) throw e
            throw TagWriteException("Could not write ID3 tags to ${file.name}", e)
        }
    }

    private fun load(file: File): Mp3File = try {
        Mp3File(file)
    } catch (e: Exception) {
        throw TagReadException("Not a readable MP3: ${file.name}", e)
    }

    private fun readV2(tag: ID3v2): TagDraft = TagDraft(
        title = safeText { tag.title }.orEmpty(),
        artist = safeText { tag.artist }.orEmpty(),
        album = safeText { tag.album }.orEmpty(),
        albumArtist = safeText { tag.albumArtist }.orEmpty(),
        year = safeText { tag.year }?.takeIf { it.isNotBlank() }
            ?: rawText(tag, TYER_IN_V24)?.take(4),
        genre = safeText { tag.genreDescription }?.takeIf { it.isNotBlank() }
            ?: rawText(tag, AbstractID3v2Tag.ID_GENRE),
        trackNumber = parseTrack(safeText { tag.track }),
        artwork = readArtwork(tag),
    )

    private fun readV1(tag: ID3v1): TagDraft = TagDraft(
        title = safeText { tag.title }.orEmpty(),
        artist = safeText { tag.artist }.orEmpty(),
        album = safeText { tag.album }.orEmpty(),
        year = safeText { tag.year }?.takeIf { it.isNotBlank() },
        genre = safeText { tag.genreDescription }?.takeIf { it.isNotBlank() },
        trackNumber = parseTrack(safeText { tag.track }),
    )

    private fun readArtwork(tag: ID3v2): Artwork? {
        val pictures = tag.frameSets[AbstractID3v2Tag.ID_IMAGE]
            ?.frames
            .orEmpty()
            .mapNotNull { frame ->
                runCatching { ID3v2PictureFrameData(false, frame.data) }.getOrNull()
            }
        val picture = pictures.firstOrNull {
            it.pictureType == AbstractID3v2Tag.PICTURETYPE_FRONTCOVER
        } ?: pictures.firstOrNull()
        picture?.imageData?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val mimeType = picture.mimeType?.takeIf { it.isNotBlank() } ?: DEFAULT_MIME_TYPE
            return Artwork(bytes, mimeType)
        }

        val legacyBytes = runCatching { tag.albumImage }.getOrNull()
        return legacyBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val mimeType = runCatching { tag.albumImageMimeType }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_MIME_TYPE
            Artwork(bytes, mimeType)
        }
    }

    private fun ensureV23(mp3: Mp3File): ID3v2 {
        val existing = mp3.id3v2Tag
        val tag = when {
            existing == null -> ID3v23Tag()
            existing.version == V23_VERSION -> existing
            else -> convertToV23(existing)
        }
        normalizeTextFrames(tag)
        return tag
    }

    private fun convertToV23(existing: ID3v2): ID3v23Tag {
        val converted = ID3v23Tag()
        var recordedDate: String? = null
        existing.frameSets.forEach { (frameId, frameSet) ->
            if (frameId == TYER_IN_V24) {
                recordedDate = frameSet.frames.firstOrNull()?.let { rawFrameText(it.data) }
            } else {
                converted.frameSets[frameId] = frameSet
            }
        }
        converted.padding = existing.padding
        if (converted.frameSets[AbstractID3v2Tag.ID_YEAR] == null) {
            val year = recordedDate?.take(4)?.takeIf { it.isNotBlank() }
            if (year != null) {
                writeUtf16Frame(converted, AbstractID3v2Tag.ID_YEAR, year)
            }
        }
        return converted
    }

    private fun normalizeTextFrames(tag: ID3v2) {
        TEXT_FRAME_IDS.forEach { frameId ->
            val frames = tag.frameSets[frameId]?.frames ?: return@forEach
            val texts = frames.mapNotNull { rawFrameText(it.data) }
            if (texts.isEmpty()) return@forEach
            val replacement = ID3v2FrameSet(frameId)
            texts.forEach { replacement.addFrame(utf16Frame(frameId, it)) }
            tag.frameSets[frameId] = replacement
        }
    }

    private fun writeText(tag: ID3v2, frameId: String, value: String?) {
        if (value.isNullOrBlank()) {
            tag.clearFrameSet(frameId)
        } else {
            writeUtf16Frame(tag, frameId, value)
        }
    }

    private fun writeUtf16Frame(tag: ID3v2, frameId: String, value: String) {
        val frameSet = ID3v2FrameSet(frameId)
        frameSet.addFrame(utf16Frame(frameId, value))
        tag.frameSets[frameId] = frameSet
    }

    private fun utf16Frame(frameId: String, value: String): ID3v2Frame {
        val encoded = EncodedText(EncodedText.TEXT_ENCODING_UTF_16, value)
        val payload = byteArrayOf(encoded.textEncoding) + encoded.toBytes(true, false)
        return ID3v2Frame(frameId, payload)
    }

    /**
     * mp3agic caches the serialized tag size and only clears it from its own setters.
     * Frame sets are replaced directly here, so toggle padding to force a recount.
     */
    private fun invalidateCachedLength(tag: ID3v2) {
        val padding = tag.padding
        tag.padding = !padding
        tag.padding = padding
    }

    private fun writeArtwork(tag: ID3v2, artwork: Artwork?) {
        if (artwork == null) {
            tag.clearAlbumImage()
        } else {
            tag.setAlbumImage(
                artwork.bytes,
                artwork.mimeType,
                AbstractID3v2Tag.PICTURETYPE_FRONTCOVER,
                "",
            )
        }
    }

    private fun saveAtomically(mp3: Mp3File, target: File) {
        val directory = target.absoluteFile.parentFile
            ?: throw IOException("No parent directory for ${target.name}")
        val temporary = File.createTempFile("cue-tag-", ".mp3", directory)
        try {
            mp3.save(temporary.absolutePath)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    private fun rawText(tag: ID3v2, frameId: String): String? =
        tag.frameSets[frameId]?.frames?.firstOrNull()?.let { rawFrameText(it.data) }

    private fun rawFrameText(data: ByteArray): String? =
        runCatching { ID3v2TextFrameData(false, data).text?.toString() }.getOrNull()

    private fun parseTrack(raw: String?): Int? = raw
        ?.substringBefore('/')
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    private inline fun <T> safeText(block: () -> T?): T? =
        runCatching(block).getOrNull()

    private companion object {
        const val V23_VERSION = "3.0"
        const val TYER_IN_V24 = "TDRC"
        const val DEFAULT_MIME_TYPE = "image/jpeg"

        val TEXT_FRAME_IDS = setOf(
            AbstractID3v2Tag.ID_TITLE,
            AbstractID3v2Tag.ID_ARTIST,
            AbstractID3v2Tag.ID_ALBUM,
            AbstractID3v2Tag.ID_ALBUM_ARTIST,
            AbstractID3v2Tag.ID_YEAR,
            AbstractID3v2Tag.ID_DATE,
            AbstractID3v2Tag.ID_GENRE,
            AbstractID3v2Tag.ID_TRACK,
            AbstractID3v2Tag.ID_PART_OF_SET,
            AbstractID3v2Tag.ID_COMPOSER,
            AbstractID3v2Tag.ID_PUBLISHER,
            AbstractID3v2Tag.ID_ORIGINAL_ARTIST,
            AbstractID3v2Tag.ID_COPYRIGHT,
            AbstractID3v2Tag.ID_ENCODER,
            AbstractID3v2Tag.ID_KEY,
            AbstractID3v2Tag.ID_GROUPING,
            AbstractID3v2Tag.ID_BPM,
            "TIME",
        )
    }
}
