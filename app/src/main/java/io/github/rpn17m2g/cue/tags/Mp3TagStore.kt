package io.github.rpn17m2g.cue.tags

import com.mpatric.mp3agic.AbstractID3v2Tag
import com.mpatric.mp3agic.EncodedText
import com.mpatric.mp3agic.ID3v1
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v23Tag
import com.mpatric.mp3agic.ID3v24Frame
import com.mpatric.mp3agic.ID3v2Frame
import com.mpatric.mp3agic.ID3v2FrameSet
import com.mpatric.mp3agic.ID3v2PictureFrameData
import com.mpatric.mp3agic.Mp3File
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.LyricLine
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.model.parseLrc
import io.github.rpn17m2g.cue.domain.model.toLrc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Reads and rewrites ID3 tags with mp3agic. Audio frames are copied byte-for-byte and
 * the replacement is verified before the hidden backup is removed.
 */
class Mp3TagStore internal constructor(
    private val mover: AtomicMover = DefaultAtomicMover,
    private val saver: Mp3Saver = DefaultMp3Saver,
) : TagStore {

    constructor() : this(DefaultAtomicMover, DefaultMp3Saver)

    override fun read(file: File): TagDraft? {
        val mp3 = load(file)
        val fromV2 = mp3.id3v2Tag?.let(::readV2)
        val fromV1 = mp3.id3v1Tag?.let(::readV1)
        return when {
            fromV2 != null && fromV1 != null -> merge(fromV2, fromV1)
            fromV2 != null -> fromV2
            else -> fromV1
        }
    }

    override fun writeInPlace(file: File, tag: TagDraft, fields: Set<TagField>) {
        if (fields.isEmpty()) return
        val year = if (TagField.YEAR in fields) normalizeYear(tag.year) else null
        val artworkMime = if (TagField.ARTWORK in fields && tag.artwork != null) {
            detectMime(tag.artwork.bytes)
                ?: throw IllegalArgumentException("Unsupported artwork format")
        } else {
            null
        }
        if (TagField.TRACK_NUMBER in fields) {
            if (tag.trackNumber != null) {
                require(tag.trackNumber > 0) { "trackNumber must be positive" }
            }
            if (tag.trackTotal != null) {
                require(tag.trackTotal > 0) { "trackTotal must be positive" }
            }
        }

        val target = file.absoluteFile
        val directory = target.parentFile
            ?: throw TagWriteException("No parent directory for ${target.name}")
        if (Files.isSymbolicLink(target.toPath())) {
            throw TagWriteException("Refusing to replace non-regular file: ${target.name}")
        }
        val backup = backupFor(target, directory)
        recoverOrRejectStaleBackup(target, backup)
        if (!Files.isRegularFile(target.toPath())) {
            throw TagWriteException("Refusing to replace non-regular file: ${target.name}")
        }

        try {
            val mp3 = load(target)
            val originalAudio = digest(mp3, target)
            val id3 = ensureWritableTag(mp3)
            adoptV1Fallback(mp3.id3v1Tag, id3, fields)

            if (TagField.TITLE in fields) {
                writeText(id3, AbstractID3v2Tag.ID_TITLE, tag.title)
            }
            if (TagField.ARTIST in fields) {
                writeText(id3, AbstractID3v2Tag.ID_ARTIST, tag.artist)
            }
            if (TagField.ALBUM in fields) {
                writeText(id3, AbstractID3v2Tag.ID_ALBUM, tag.album)
            }
            if (TagField.ALBUM_ARTIST in fields) {
                writeText(id3, AbstractID3v2Tag.ID_ALBUM_ARTIST, tag.albumArtist)
            }
            if (TagField.YEAR in fields) {
                writeYear(id3, year)
            }
            if (TagField.GENRE in fields) {
                writeText(id3, AbstractID3v2Tag.ID_GENRE, tag.genre)
            }
            if (TagField.TRACK_NUMBER in fields) {
                writeTrack(id3, tag)
            }
            if (TagField.ARTWORK in fields) {
                writeArtwork(id3, tag.artwork, artworkMime)
            }
            if (TagField.LYRICS in fields) {
                writeLyrics(id3, tag.lyrics, tag.syncedLyrics)
            }
            if (TagField.BPM in fields) {
                writeBpm(id3, tag.bpm)
            }
            if (TagField.ISRC in fields) {
                writeText(id3, ID_ISRC, tag.isrc)
            }
            if (TagField.LABEL in fields) {
                writeText(id3, AbstractID3v2Tag.ID_PUBLISHER, tag.label)
            }
            if (TagField.MUSICBRAINZ in fields) {
                writeMusicBrainz(id3, tag)
            }
            if (TagField.REPLAY_GAIN in fields) {
                writeReplayGain(id3, tag.replayGainDb)
            }

            if (id3.frameSets.isEmpty()) {
                id3.padding = true
            }
            invalidateCachedLength(id3)

            mp3.id3v2Tag = id3
            mp3.removeId3v1Tag()
            saveAndVerify(mp3, target, directory, backup, originalAudio)
        } catch (e: TagReadException) {
            throw e
        } catch (e: TagWriteException) {
            throw e
        } catch (e: Exception) {
            throw TagWriteException("Could not write ID3 tags to ${target.name}", e)
        }
    }

    private fun load(file: File): Mp3File = try {
        Mp3File(file)
    } catch (e: Exception) {
        throw TagReadException("Not a readable MP3: ${file.name}", e)
    }

    private fun readV2(tag: ID3v2): TagDraft {
        val track = parseTrack(safeText { tag.track }.orEmpty())
        val syncedLyrics = readSyncedLyrics(tag)
        val lyrics = safeText { tag.lyrics }?.takeIf { it.isNotBlank() }
            ?: syncedLyrics?.let { lrc ->
                parseLrc(lrc).joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
            }
        return TagDraft(
            title = safeText { tag.title }.orEmpty(),
            artist = safeText { tag.artist }.orEmpty(),
            album = safeText { tag.album }.orEmpty(),
            albumArtist = safeText { tag.albumArtist }.orEmpty(),
            year = normalizeReadYear(
                safeText { tag.year }?.takeIf { it.isNotBlank() }
                    ?: rawText(tag, TYER_IN_V24),
            ),
            genre = safeText { tag.genreDescription }?.takeIf { it.isNotBlank() }
                ?: rawText(tag, AbstractID3v2Tag.ID_GENRE),
            trackNumber = track.first,
            trackTotal = track.second,
            artwork = readArtwork(tag),
            lyrics = lyrics,
            syncedLyrics = syncedLyrics,
            bpm = runCatching { tag.bpm }.getOrNull()?.takeIf { it > 0 },
            isrc = rawText(tag, ID_ISRC)?.takeIf { it.isNotBlank() },
            label = safeText { tag.publisher }?.takeIf { it.isNotBlank() }
                ?: rawText(tag, AbstractID3v2Tag.ID_PUBLISHER)?.takeIf { it.isNotBlank() },
            replayGainDb = readCustomText(tag, REPLAY_GAIN_TRACK)?.let(::parseReplayGain),
            recordingMbid = readCustomText(tag, MB_RECORDING_ID)?.takeIf { it.isNotBlank() },
            releaseMbid = readCustomText(tag, MB_ALBUM_ID)?.takeIf { it.isNotBlank() },
            artistMbid = readCustomText(tag, MB_ARTIST_ID)?.takeIf { it.isNotBlank() },
        )
    }

    private fun readV1(tag: ID3v1): TagDraft {
        val track = parseTrack(safeText { tag.track }.orEmpty())
        return TagDraft(
            title = safeText { tag.title }.orEmpty(),
            artist = safeText { tag.artist }.orEmpty(),
            album = safeText { tag.album }.orEmpty(),
            year = normalizeReadYear(safeText { tag.year }),
            genre = safeText { tag.genreDescription }?.takeIf { it.isNotBlank() },
            trackNumber = track.first,
            trackTotal = track.second,
        )
    }

    /** ID3v1 is a fallback only for values the v2 tag does not provide. */
    private fun merge(v2: TagDraft, v1: TagDraft): TagDraft = v2.copy(
        title = v2.title.ifBlank { v1.title },
        artist = v2.artist.ifBlank { v1.artist },
        album = v2.album.ifBlank { v1.album },
        year = v2.year ?: v1.year,
        genre = v2.genre ?: v1.genre,
        trackNumber = v2.trackNumber ?: v1.trackNumber,
        trackTotal = v2.trackTotal ?: v1.trackTotal,
    )

    /**
     * Copies ID3v1 values that are about to be discarded into blank v2 fields. Values
     * selected for an update are never taken from v1: a selected blank means clear.
     */
    private fun adoptV1Fallback(v1: ID3v1?, id3: ID3v2, fields: Set<TagField>) {
        if (v1 == null) return
        val fallback = readV1(v1)
        if (TagField.TITLE !in fields &&
            safeText { id3.title }.isNullOrBlank() &&
            fallback.title.isNotBlank()
        ) {
            writeText(id3, AbstractID3v2Tag.ID_TITLE, fallback.title)
        }
        if (TagField.ARTIST !in fields &&
            safeText { id3.artist }.isNullOrBlank() &&
            fallback.artist.isNotBlank()
        ) {
            writeText(id3, AbstractID3v2Tag.ID_ARTIST, fallback.artist)
        }
        if (TagField.ALBUM !in fields &&
            safeText { id3.album }.isNullOrBlank() &&
            fallback.album.isNotBlank()
        ) {
            writeText(id3, AbstractID3v2Tag.ID_ALBUM, fallback.album)
        }
        if (TagField.YEAR !in fields) {
            val currentYear = normalizeReadYear(rawText(id3, AbstractID3v2Tag.ID_YEAR))
                ?: normalizeReadYear(rawText(id3, TYER_IN_V24))
            if (currentYear == null && fallback.year != null) {
                writeYear(id3, fallback.year)
            }
        }
        if (TagField.GENRE !in fields &&
            safeText { id3.genreDescription }.isNullOrBlank() &&
            fallback.genre != null
        ) {
            writeText(id3, AbstractID3v2Tag.ID_GENRE, fallback.genre)
        }
        if (TagField.TRACK_NUMBER !in fields &&
            parseTrack(safeText { id3.track }.orEmpty()).first == null &&
            fallback.trackNumber != null
        ) {
            writeText(id3, AbstractID3v2Tag.ID_TRACK, fallback.trackNumber.toString())
        }
    }

    private fun readArtwork(tag: ID3v2): Artwork? {
        val pictures = tag.frameSets[AbstractID3v2Tag.ID_IMAGE]
            ?.frames
            .orEmpty()
            .mapNotNull { frame ->
                runCatching { ID3v2PictureFrameData(false, frame.data) }.getOrNull()
            }
            .filter { it.imageData.isNotEmpty() }
        val picture = pictures.firstOrNull {
            it.pictureType == AbstractID3v2Tag.PICTURETYPE_FRONTCOVER
        } ?: pictures.firstOrNull()
        picture?.let {
            return Artwork(
                it.imageData,
                detectMime(it.imageData) ?: it.mimeType ?: DEFAULT_MIME_TYPE,
            )
        }

        val legacyBytes = runCatching { tag.albumImage }.getOrNull()
        return legacyBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val declared = runCatching { tag.albumImageMimeType }.getOrNull()
            Artwork(bytes, detectMime(bytes) ?: declared ?: DEFAULT_MIME_TYPE)
        }
    }

    private fun ensureWritableTag(mp3: Mp3File): ID3v2 {
        val existing = mp3.id3v2Tag
        return when {
            existing == null -> ID3v23Tag()
            existing.version == V23_VERSION || existing.version == V24_VERSION -> existing
            else -> throw UnsupportedTagVersionException(
                existing.version,
                "ID3 ${existing.version} is not supported; refusing to rewrite it",
            )
        }
    }

    private fun writeText(tag: ID3v2, frameId: String, value: String?) {
        if (value.isNullOrBlank()) {
            tag.clearFrameSet(frameId)
        } else {
            writeUtf16Frame(tag, frameId, value)
        }
    }

    private fun writeYear(tag: ID3v2, year: String?) {
        tag.clearFrameSet(AbstractID3v2Tag.ID_YEAR)
        tag.clearFrameSet(TYER_IN_V24)
        if (year != null) {
            val frameId = if (tag.version == V24_VERSION) TYER_IN_V24 else AbstractID3v2Tag.ID_YEAR
            writeUtf16Frame(tag, frameId, year)
        }
    }

    /**
     * Lyrics are written twice: `USLT` carries the plain multi-line text that
     * every player reads, and `SYLT` carries the same lines with millisecond
     * timestamps for players that highlight the current one. mp3agic has no
     * SYLT support, so the frame is assembled and parsed here.
     */
    private fun writeLyrics(tag: ID3v2, lyrics: String?, syncedLyrics: String?) {
        if (lyrics.isNullOrBlank()) {
            tag.clearFrameSet(AbstractID3v2Tag.ID_TEXT_LYRICS)
        } else {
            tag.lyrics = lyrics
        }
        val lines = syncedLyrics?.let(::parseLrc).orEmpty()
        if (lines.isEmpty()) {
            tag.clearFrameSet(ID_SYLT)
        } else {
            tag.frameSets[ID_SYLT] = ID3v2FrameSet(ID_SYLT).also {
                it.addFrame(frameFor(tag, ID_SYLT, syltPayload(lines)))
            }
        }
    }

    private fun syltPayload(lines: List<LyricLine>): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(EncodedText.TEXT_ENCODING_UTF_16.toInt())
        payload.write(LANGUAGE_UNDETERMINED.toByteArray(Charsets.ISO_8859_1))
        payload.write(TIMESTAMP_FORMAT_MILLISECONDS)
        payload.write(CONTENT_TYPE_LYRICS)
        payload.write(utf16Terminated(""))
        lines.forEach { line ->
            payload.write(utf16Terminated(line.text))
            val time = line.startMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            payload.write(time ushr 24)
            payload.write(time ushr 16)
            payload.write(time ushr 8)
            payload.write(time)
        }
        return payload.toByteArray()
    }

    /**
     * One UTF-16 string in a SYLT frame: BOM, the text and a two-byte
     * terminator. The BOM is repeated per string, as other taggers do.
     */
    private fun utf16Terminated(value: String): ByteArray =
        UTF16_BOM + value.toByteArray(Charsets.UTF_16LE) + TERMINATOR_UTF16

    private fun readSyncedLyrics(tag: ID3v2): String? {
        val data = tag.frameSets[ID_SYLT]?.frames?.firstOrNull()?.data ?: return null
        val lines = runCatching { parseSylt(data) }.getOrNull().orEmpty()
        return lines.takeIf { it.isNotEmpty() }?.toLrc()
    }

    private fun parseSylt(data: ByteArray): List<LyricLine> {
        if (data.size < SYLT_HEADER_SIZE) return emptyList()
        val encoding = data[0]
        if ((data[4].toInt() and 0xFF) != TIMESTAMP_FORMAT_MILLISECONDS) return emptyList()
        var index = readSyltText(data, SYLT_HEADER_SIZE, encoding)?.second
            ?: return emptyList()
        val lines = mutableListOf<LyricLine>()
        while (index + 4 <= data.size) {
            val (text, next) = readSyltText(data, index, encoding) ?: break
            index = next
            if (index + 4 > data.size) break
            val start = (
                (data[index].toLong() and 0xFF) shl 24 or
                    ((data[index + 1].toLong() and 0xFF) shl 16) or
                    ((data[index + 2].toLong() and 0xFF) shl 8) or
                    (data[index + 3].toLong() and 0xFF)
                )
            index += 4
            if (text.isNotBlank()) lines += LyricLine(start, text)
        }
        return lines.sortedBy { it.startMs }
    }

    /** Reads one terminator-delimited string and returns it with the next offset. */
    private fun readSyltText(
        data: ByteArray,
        start: Int,
        encoding: Byte,
    ): Pair<String, Int>? {
        return when (encoding.toInt()) {
            EncodedText.TEXT_ENCODING_UTF_16.toInt() -> {
                var end = start
                while (end + 1 < data.size &&
                    !(data[end].toInt() == 0 && data[end + 1].toInt() == 0)
                ) {
                    end += 2
                }
                if (end + 1 >= data.size) return null
                decodeUtf16(data.copyOfRange(start, end)) to end + 2
            }
            EncodedText.TEXT_ENCODING_UTF_16BE.toInt() -> {
                var end = start
                while (end + 1 < data.size &&
                    !(data[end].toInt() == 0 && data[end + 1].toInt() == 0)
                ) {
                    end += 2
                }
                if (end + 1 >= data.size) return null
                String(data, start, end - start, Charsets.UTF_16BE) to end + 2
            }
            EncodedText.TEXT_ENCODING_UTF_8.toInt() -> {
                val end = data.indexOf(0, start)
                if (end < 0) return null
                String(data, start, end - start, Charsets.UTF_8) to end + 1
            }
            else -> {
                val end = data.indexOf(0, start)
                if (end < 0) return null
                String(data, start, end - start, Charsets.ISO_8859_1) to end + 1
            }
        }
    }

    private fun decodeUtf16(bytes: ByteArray): String {
        val body = if (bytes.size % 2 == 0) bytes else bytes.copyOf(bytes.size - 1)
        return when {
            body.size >= 2 &&
                (body[0].toInt() and 0xFF) == 0xFF &&
                (body[1].toInt() and 0xFF) == 0xFE ->
                String(body, 2, body.size - 2, Charsets.UTF_16LE)
            body.size >= 2 &&
                (body[0].toInt() and 0xFF) == 0xFE &&
                (body[1].toInt() and 0xFF) == 0xFF ->
                String(body, 2, body.size - 2, Charsets.UTF_16BE)
            else -> String(body, Charsets.UTF_16BE)
        }
    }

    private fun ByteArray.indexOf(value: Int, from: Int): Int {
        for (index in from until size) {
            if ((this[index].toInt() and 0xFF) == value) return index
        }
        return -1
    }

    private fun writeBpm(tag: ID3v2, bpm: Int?) {
        if (bpm == null || bpm <= 0) {
            tag.clearFrameSet(AbstractID3v2Tag.ID_BPM)
        } else {
            tag.bpm = bpm
        }
    }

    private fun writeMusicBrainz(tag: ID3v2, draft: TagDraft) {
        writeCustomText(tag, MB_RECORDING_ID, draft.recordingMbid)
        writeCustomText(tag, MB_ALBUM_ID, draft.releaseMbid)
        writeCustomText(tag, MB_ARTIST_ID, draft.artistMbid)
    }

    private fun writeReplayGain(tag: ID3v2, gainDb: Double?) {
        val value = gainDb?.let { String.format(Locale.ROOT, "%.1f dB", it) }
        writeCustomText(tag, REPLAY_GAIN_TRACK, value)
    }

    /**
     * A TXXX frame carries a description and a value in the same encoding. The
     * identifiers Cue writes are ASCII, so the frame is written as ISO-8859-1;
     * reading accepts the UTF-16/UTF-8 variants other taggers produce.
     */
    private fun writeCustomText(tag: ID3v2, description: String, value: String?) {
        val remaining = tag.frameSets[ID_TXXX]
            ?.frames
            .orEmpty()
            .filterNot { frame ->
                runCatching {
                    parseTxxx(frame.data)?.first.equals(description, ignoreCase = true)
                }.getOrDefault(false)
            }
        if (remaining.isEmpty()) {
            tag.clearFrameSet(ID_TXXX)
        } else {
            tag.frameSets[ID_TXXX] = ID3v2FrameSet(ID_TXXX).also { set ->
                remaining.forEach(set::addFrame)
            }
        }
        if (value.isNullOrBlank()) return
        val payload = byteArrayOf(EncodedText.TEXT_ENCODING_ISO_8859_1) +
            description.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0) +
            value.toByteArray(Charsets.ISO_8859_1)
        val frames = tag.frameSets[ID_TXXX]
            ?: ID3v2FrameSet(ID_TXXX).also { tag.frameSets[ID_TXXX] = it }
        frames.addFrame(frameFor(tag, ID_TXXX, payload))
    }

    private fun readCustomText(tag: ID3v2, description: String): String? {
        val frames = tag.frameSets[ID_TXXX]?.frames.orEmpty()
        for (frame in frames) {
            val parsed = runCatching { parseTxxx(frame.data) }.getOrNull() ?: continue
            if (parsed.first.equals(description, ignoreCase = true)) return parsed.second
        }
        return null
    }

    private fun parseTxxx(data: ByteArray): Pair<String, String>? {
        if (data.size < 2) return null
        val encoding = data[0]
        val utf16 = encoding == EncodedText.TEXT_ENCODING_UTF_16 ||
            encoding == EncodedText.TEXT_ENCODING_UTF_16BE
        val start = if (encoding == EncodedText.TEXT_ENCODING_UTF_16) 3 else 1
        val step = if (utf16) 2 else 1
        var separator = -1
        var index = start
        while (index < data.size) {
            // A UTF-16 frame separates the description from the value with two
            // null bytes; the single-byte encodings use one. Looking for two in
            // an ISO-8859-1 frame misses the separator entirely.
            val found = if (utf16) {
                index + 1 < data.size &&
                    data[index].toInt() == 0 &&
                    data[index + 1].toInt() == 0
            } else {
                data[index].toInt() == 0
            }
            if (found) {
                separator = index
                break
            }
            index += step
        }
        if (separator < 0) return null
        val description = decodeText(encoding, data.copyOfRange(start, separator))
        val valueStart = separator + if (utf16) 2 else 1
        val value = decodeText(encoding, data.copyOfRange(valueStart, data.size))
        return description to value
    }

    private fun decodeText(encoding: Byte, bytes: ByteArray): String = runCatching {
        EncodedText(encoding, bytes).toString()
    }.getOrDefault("")

    private fun parseReplayGain(value: String): Double? =
        REPLAY_GAIN_NUMBER.find(value)?.value?.toDoubleOrNull()

    private fun writeTrack(tag: ID3v2, draft: TagDraft) {
        val number = draft.trackNumber
        val existingTotal = parseTrack(
            rawText(tag, AbstractID3v2Tag.ID_TRACK).orEmpty(),
        ).second
        if (number == null) {
            if (draft.trackTotal == null) {
                tag.clearFrameSet(AbstractID3v2Tag.ID_TRACK)
            } else {
                writeUtf16Frame(tag, AbstractID3v2Tag.ID_TRACK, "/${draft.trackTotal}")
            }
            return
        }
        val total = draft.trackTotal ?: existingTotal
        writeUtf16Frame(
            tag,
            AbstractID3v2Tag.ID_TRACK,
            if (total == null) number.toString() else "$number/$total",
        )
    }

    private fun writeUtf16Frame(tag: ID3v2, frameId: String, value: String) {
        val frameSet = ID3v2FrameSet(frameId)
        frameSet.addFrame(utf16Frame(tag, frameId, value))
        tag.frameSets[frameId] = frameSet
    }

    private fun utf16Frame(tag: ID3v2, frameId: String, value: String): ID3v2Frame {
        val encoded = EncodedText(EncodedText.TEXT_ENCODING_UTF_16, value)
        val payload = byteArrayOf(encoded.textEncoding) + encoded.toBytes(true, false)
        return frameFor(tag, frameId, payload)
    }

    private fun frameFor(tag: ID3v2, frameId: String, payload: ByteArray): ID3v2Frame =
        if (tag.version == V24_VERSION) {
            ID3v24Frame(frameId, payload)
        } else {
            ID3v2Frame(frameId, payload)
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

    private fun writeArtwork(tag: ID3v2, artwork: Artwork?, mimeType: String?) {
        val preserved = nonFrontPictures(tag)
        if (artwork == null) {
            tag.clearAlbumImage()
            appendPictures(tag, preserved)
            return
        }
        val mime = mimeType ?: throw IllegalArgumentException("Unsupported artwork format")
        tag.setAlbumImage(
            artwork.bytes,
            mime,
            AbstractID3v2Tag.PICTURETYPE_FRONTCOVER,
            "",
        )
        appendPictures(tag, preserved)
    }

    private fun nonFrontPictures(tag: ID3v2): List<ID3v2Frame> =
        tag.frameSets[AbstractID3v2Tag.ID_IMAGE]
            ?.frames
            .orEmpty()
            .mapNotNull { frame ->
                val picture = runCatching {
                    ID3v2PictureFrameData(false, frame.data)
                }.getOrNull()
                if (picture?.pictureType == AbstractID3v2Tag.PICTURETYPE_FRONTCOVER) {
                    null
                } else {
                    frameFor(tag, AbstractID3v2Tag.ID_IMAGE, frame.data)
                }
            }

    private fun appendPictures(tag: ID3v2, pictures: List<ID3v2Frame>) {
        if (pictures.isEmpty()) return
        val images = tag.frameSets[AbstractID3v2Tag.ID_IMAGE]
            ?: ID3v2FrameSet(AbstractID3v2Tag.ID_IMAGE).also {
                tag.frameSets[AbstractID3v2Tag.ID_IMAGE] = it
            }
        pictures.forEach(images::addFrame)
    }

    private fun saveAndVerify(
        mp3: Mp3File,
        target: File,
        directory: File,
        backup: File,
        originalAudio: ByteArray,
    ) {
        if (!directory.canWrite()) {
            throw TagWriteException("Directory is not writable: ${directory.path}")
        }
        val prefix = TEMP_PREFIX + target.name + "-"
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(TEMP_SUFFIX) }
            ?.forEach { it.delete() }

        val temporary = Files.createTempFile(directory.toPath(), prefix, TEMP_SUFFIX)
        var keepBackup = false
        var backupCreated = false
        try {
            forceDirectory(directory.toPath())
            saver.save(mp3, temporary)
            force(temporary)
            if (!digest(Mp3File(temporary), temporary.toFile()).contentEquals(originalAudio)) {
                throw TagWriteException("Audio changed while writing ${target.name}")
            }

            if (backup.exists()) {
                throw TagWriteException(
                    "Stale backup exists at ${backup.path}; refusing to overwrite it",
                )
            }
            backupCreated = true
            Files.copy(target.toPath(), backup.toPath())
            force(backup.toPath())
            if (!digest(Mp3File(backup), backup).contentEquals(originalAudio)) {
                throw TagWriteException("Could not back up ${target.name} safely")
            }

            mover.move(temporary, target.toPath())
            forceDirectory(directory.toPath())
            if (!digest(Mp3File(target), target).contentEquals(originalAudio)) {
                throw TagWriteException("Audio verification failed for ${target.name}")
            }
        } catch (e: Exception) {
            if (!target.exists() || !audioMatches(target, originalAudio)) {
                if (!restore(backup, target, originalAudio)) {
                    keepBackup = backup.exists()
                    val backupStatus = if (keepBackup) {
                        "backup kept at ${backup.path}"
                    } else {
                        "backup is no longer available"
                    }
                    throw TagWriteException(
                        "Could not restore ${target.name}; $backupStatus",
                        e,
                    )
                }
            }
            if (e is TagWriteException) throw e
            throw TagWriteException("Could not replace ${target.name}", e)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
            if (backupCreated && !keepBackup) {
                runCatching { Files.deleteIfExists(backup.toPath()) }
            }
        }
    }

    private fun recoverOrRejectStaleBackup(target: File, backup: File) {
        if (!backup.exists()) return
        if (target.exists()) {
            throw TagWriteException(
                "Stale backup exists at ${backup.path}; remove it explicitly after verifying ${target.name}",
            )
        }
        try {
            Mp3File(backup)
        } catch (e: Exception) {
            throw TagWriteException(
                "Backup ${backup.path} is not a readable MP3; refusing to recover it",
                e,
            )
        }
        try {
            mover.move(backup.toPath(), target.toPath())
        } catch (e: Exception) {
            throw TagWriteException("Could not recover ${target.name} from ${backup.path}", e)
        }
    }

    private fun restore(backup: File, target: File, originalAudio: ByteArray): Boolean {
        if (!backup.exists()) return false
        if (!audioMatches(backup, originalAudio)) return false
        return runCatching {
            mover.move(backup.toPath(), target.toPath())
            audioMatches(target, originalAudio)
        }.getOrDefault(false)
    }

    private fun backupFor(target: File, directory: File): File =
        File(directory, BACKUP_PREFIX + target.name + BACKUP_SUFFIX)

    private fun audioMatches(file: File, expected: ByteArray): Boolean =
        runCatching { digest(Mp3File(file), file).contentEquals(expected) }.getOrDefault(false)

    /** Hashes only the MPEG frames, excluding ID3 tags and any pre-audio junk. */
    private fun digest(mp3: Mp3File, file: File): ByteArray {
        val start = mp3.startOffset
        val length = mp3.endOffset - start + 1
        if (start < 0 || length <= 0) {
            throw TagReadException("MP3 contains no audio frames")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            channel.position(start.toLong())
            var remaining = length.toLong()
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (remaining > 0) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val read = channel.read(buffer)
                if (read < 0) throw IOException("Unexpected end of audio data")
                if (read == 0) continue
                buffer.flip()
                digest.update(buffer)
                remaining -= read
            }
        }
        return digest.digest()
    }

    private fun force(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
    }

    private fun forceDirectory(directory: Path) {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: UnsupportedOperationException) {
        } catch (e: IOException) {
            if (!IS_WINDOWS) throw e
        }
    }

    private fun rawText(tag: ID3v2, frameId: String): String? =
        tag.frameSets[frameId]?.frames?.firstOrNull()?.let { rawFrameText(it.data) }

    private fun rawFrameText(data: ByteArray): String? =
        runCatching {
            com.mpatric.mp3agic.ID3v2TextFrameData(false, data).text?.toString()
        }.getOrNull()

    private fun parseTrack(raw: String): Pair<Int?, Int?> {
        val value = raw.trim()
        if (value.isEmpty()) return null to null
        val separator = value.indexOf('/')
        val number = value.substring(0, if (separator < 0) value.length else separator)
            .trim()
            .toIntOrNull()
            ?.takeIf { it > 0 }
        val total = if (separator < 0) {
            null
        } else {
            value.substring(separator + 1).trim().toIntOrNull()?.takeIf { it > 0 }
        }
        return number to total
    }

    private fun normalizeYear(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val match = YEAR_PATTERN.matchEntire(value.trim())
            ?: throw IllegalArgumentException("Year must be YYYY or an ISO date")
        return match.groupValues[1]
    }

    private fun normalizeReadYear(value: String?): String? = value
        ?.trim()
        ?.take(4)
        ?.takeIf { it.length == 4 && it.all(Char::isDigit) }

    private fun detectMime(bytes: ByteArray): String? {
        fun startsWith(vararg values: Int): Boolean = bytes.size >= values.size &&
            values.indices.all { (bytes[it].toInt() and 0xFF) == values[it] }
        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            bytes.size >= 12 &&
                "RIFF".toByteArray().contentEquals(bytes.copyOfRange(0, 4)) &&
                "WEBP".toByteArray().contentEquals(bytes.copyOfRange(8, 12)) -> "image/webp"
            else -> null
        }
    }

    private inline fun <T> safeText(block: () -> T?): T? = runCatching(block).getOrNull()

    internal fun interface AtomicMover {
        fun move(source: Path, target: Path)
    }

    internal fun interface Mp3Saver {
        fun save(mp3: Mp3File, target: Path)
    }

    internal object DefaultMp3Saver : Mp3Saver {
        override fun save(mp3: Mp3File, target: Path) {
            mp3.save(target.toAbsolutePath().toString())
        }
    }

    internal object DefaultAtomicMover : AtomicMover {
        override fun move(source: Path, target: Path) {
            try {
                Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                throw TagWriteException(
                    "Atomic file replacement is not supported for ${target.fileName}",
                    e,
                )
            }
        }
    }

    private companion object {
        const val V23_VERSION = "3.0"
        const val V24_VERSION = "4.0"
        const val TYER_IN_V24 = "TDRC"
        const val ID_ISRC = "TSRC"
        const val ID_TXXX = "TXXX"
        const val ID_SYLT = "SYLT"
        const val LANGUAGE_UNDETERMINED = "und"
        const val TIMESTAMP_FORMAT_MILLISECONDS = 2
        const val CONTENT_TYPE_LYRICS = 1
        const val SYLT_HEADER_SIZE = 6
        val UTF16_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val TERMINATOR_UTF16 = byteArrayOf(0, 0)
        const val MB_RECORDING_ID = "MusicBrainz Recording Id"
        const val MB_ALBUM_ID = "MusicBrainz Album Id"
        const val MB_ARTIST_ID = "MusicBrainz Artist Id"
        const val REPLAY_GAIN_TRACK = "REPLAYGAIN_TRACK_GAIN"
        val REPLAY_GAIN_NUMBER = Regex("""[-+]?\d+(?:\.\d+)?""")
        const val DEFAULT_MIME_TYPE = "image/jpeg"
        const val TEMP_PREFIX = ".cue-tag-"
        const val TEMP_SUFFIX = ".tmp"
        const val BACKUP_PREFIX = ".cue-tag-"
        const val BACKUP_SUFFIX = ".bak"

        val IS_WINDOWS = System.getProperty("os.name").orEmpty()
            .startsWith("Windows", ignoreCase = true)
        val YEAR_PATTERN = Regex("""^(\d{4})(?:-\d{2}(?:-\d{2})?)?$""")
    }
}
