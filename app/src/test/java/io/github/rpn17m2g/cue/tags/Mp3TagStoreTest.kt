package io.github.rpn17m2g.cue.tags

import com.mpatric.mp3agic.AbstractID3v2Tag
import com.mpatric.mp3agic.EncodedText
import com.mpatric.mp3agic.ID3v22Tag
import com.mpatric.mp3agic.ID3v23Tag
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.ID3v2Frame
import com.mpatric.mp3agic.ID3v2FrameSet
import com.mpatric.mp3agic.ID3v2PictureFrameData
import com.mpatric.mp3agic.Mp3File
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp3TagStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store = Mp3TagStore()

    @Test
    fun `reading an mp3 without any tag returns null`() {
        val file = taggedFile("untagged.mp3", Mp3Fixtures.audioOnly())

        assertNull(store.read(file))
    }

    @Test
    fun `malformed file throws a typed read exception`() {
        val file = taggedFile("broken.mp3", "clearly not an mp3 frame\n".repeat(200).toByteArray())

        assertThrows(TagReadException::class.java) { store.read(file) }
    }

    @Test
    fun `writing a malformed file throws a read exception and leaves it untouched`() {
        val file = taggedFile("broken-write.mp3", "clearly not an mp3 frame\n".repeat(200).toByteArray())
        val original = file.readBytes()

        assertThrows(TagReadException::class.java) {
            store.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
        }
        assertArrayEquals(original, file.readBytes())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `round trip keeps every field including hebrew artwork and track total`() {
        val file = taggedFile("roundtrip.mp3")
        val artworkBytes = TestImages.png(width = 24, height = 16)
        val draft = TagDraft(
            title = "שלום עולם",
            artist = "אמן אלמוני",
            album = "אלבום ראשון",
            albumArtist = "Various Artists",
            year = "2024",
            genre = "Rock",
            trackNumber = 7,
            trackTotal = 12,
            artwork = Artwork(artworkBytes, "image/png"),
        )

        store.writeInPlace(file, draft, TagField.ALL_FIELDS)

        val read = checkNotNull(store.read(file))
        assertEquals("שלום עולם", read.title)
        assertEquals("אמן אלמוני", read.artist)
        assertEquals("אלבום ראשון", read.album)
        assertEquals("Various Artists", read.albumArtist)
        assertEquals("2024", read.year)
        assertEquals("Rock", read.genre)
        assertEquals(7, read.trackNumber)
        assertEquals(12, read.trackTotal)
        assertNotNull(read.artwork)
        assertArrayEquals(artworkBytes, read.artwork!!.bytes)
        assertEquals("image/png", read.artwork!!.mimeType)
    }

    @Test
    fun `written tag is v23 with utf16 text frames`() {
        val file = taggedFile("version.mp3")
        store.writeInPlace(
            file,
            TagDraft(
                title = "כותרת",
                artist = "אמן",
                album = "אלבום",
                albumArtist = "אמן אלבום",
                year = "2023",
                genre = "Jazz",
                trackNumber = 2,
            ),
            TagField.ALL_FIELDS,
        )

        val mp3 = Mp3File(file)
        val tag = checkNotNull(mp3.id3v2Tag)
        assertEquals("3.0", tag.version)

        val header = file.readBytes()
        assertEquals(3, header[3].toInt())
        assertEquals(0, header[4].toInt())

        val frameIds = listOf(
            AbstractID3v2Tag.ID_TITLE,
            AbstractID3v2Tag.ID_ARTIST,
            AbstractID3v2Tag.ID_ALBUM,
            AbstractID3v2Tag.ID_ALBUM_ARTIST,
            AbstractID3v2Tag.ID_YEAR,
            AbstractID3v2Tag.ID_GENRE,
            AbstractID3v2Tag.ID_TRACK,
        )
        frameIds.forEach { frameId ->
            val data = checkNotNull(tag.frameSets[frameId])
                .frames
                .first()
                .data
            assertEquals(
                "frame $frameId should use UTF-16",
                EncodedText.TEXT_ENCODING_UTF_16.toInt(),
                data[0].toInt(),
            )
            assertEquals(0xFF, data[1].toInt() and 0xFF)
            assertEquals(0xFE, data[2].toInt() and 0xFF)
        }
    }

    @Test
    fun `updating a subset keeps untouched fields and artwork`() {
        val file = taggedFile("subset.mp3")
        val artworkBytes = TestImages.png(width = 8, height = 8)
        store.writeInPlace(
            file,
            TagDraft(
                title = "Original Title",
                artist = "Original Artist",
                album = "Original Album",
                albumArtist = "Original Album Artist",
                year = "2001",
                genre = "Pop",
                trackNumber = 4,
                trackTotal = 9,
                artwork = Artwork(artworkBytes, "image/png"),
            ),
            TagField.ALL_FIELDS,
        )

        store.writeInPlace(
            file,
            TagDraft(title = "New Title"),
            fields = setOf(TagField.TITLE),
        )

        val read = checkNotNull(store.read(file))
        assertEquals("New Title", read.title)
        assertEquals("Original Artist", read.artist)
        assertEquals("Original Album", read.album)
        assertEquals("Original Album Artist", read.albumArtist)
        assertEquals("2001", read.year)
        assertEquals("Pop", read.genre)
        assertEquals(4, read.trackNumber)
        assertEquals(9, read.trackTotal)
        assertArrayEquals(artworkBytes, read.artwork!!.bytes)
    }

    @Test
    fun `artwork is cleared only when artwork is in the updated fields`() {
        val file = taggedFile("artwork.mp3")
        store.writeInPlace(
            file,
            TagDraft(
                title = "Song",
                artist = "Artist",
                album = "Album",
                artwork = Artwork(TestImages.jpeg(width = 16, height = 16), "image/jpeg"),
            ),
            TagField.ALL_FIELDS,
        )

        store.writeInPlace(file, TagDraft(title = "Song Only"), fields = setOf(TagField.TITLE))
        assertNotNull(store.read(file)!!.artwork)

        store.writeInPlace(
            file,
            TagDraft(title = "Song Only", artwork = null),
            fields = setOf(TagField.ARTWORK),
        )
        assertNull(store.read(file)!!.artwork)
    }

    @Test
    fun `empty text fields remove their frames`() {
        val file = taggedFile("empty.mp3")
        store.writeInPlace(
            file,
            TagDraft(title = "Song", artist = "Artist", genre = "Rock"),
            TagField.ALL_FIELDS,
        )

        store.writeInPlace(
            file,
            TagDraft(title = "", artist = "", genre = null, trackNumber = null),
            fields = setOf(TagField.TITLE, TagField.ARTIST, TagField.GENRE, TagField.TRACK_NUMBER),
        )

        val read = checkNotNull(store.read(file))
        assertEquals("", read.title)
        assertEquals("", read.artist)
        assertNull(read.genre)
        assertNull(read.trackNumber)
    }

    @Test
    fun `writing to an untagged file creates a v23 tag`() {
        val file = taggedFile("create.mp3")

        store.writeInPlace(
            file,
            TagDraft(title = "Fresh", artist = "New", album = "Tag"),
            TagField.ALL_FIELDS,
        )

        val read = checkNotNull(store.read(file))
        assertEquals("Fresh", read.title)
        assertEquals("New", read.artist)
        assertEquals("Tag", read.album)
        assertEquals("3.0", Mp3File(file).id3v2Tag.version)
    }

    @Test
    fun `id3v1 is used when no v2 tag exists`() {
        val bytes = Mp3Fixtures.withId3v1(
            audio = Mp3Fixtures.audioOnly(),
            title = "Legacy Title",
            artist = "Legacy Artist",
            album = "Legacy Album",
            year = "1998",
            genre = 17,
            trackNumber = 5,
        )
        val file = taggedFile("v1.mp3", bytes)

        val read = checkNotNull(store.read(file))
        assertEquals("Legacy Title", read.title)
        assertEquals("Legacy Artist", read.artist)
        assertEquals("Legacy Album", read.album)
        assertEquals("1998", read.year)
        assertEquals("Rock", read.genre)
        assertEquals(5, read.trackNumber)
        assertNull(read.artwork)
    }

    @Test
    fun `blank v2 fields fall back to v1 and v1 is migrated before removal`() {
        val v1 = Mp3Fixtures.withId3v1(
            audio = Mp3Fixtures.audioOnly(),
            title = "V1 Title",
            artist = "V1 Artist",
            album = "V1 Album",
            year = "1990",
            genre = 17,
            trackNumber = 5,
        )
        val v2 = ID3v23Tag().also {
            it.padding = true
            it.setTitle("V2 Title")
        }
        val file = taggedFile("v1-v2.mp3", v2.toBytes() + v1)
        val originalAudio = audioBytes(file)

        val merged = checkNotNull(store.read(file))
        assertEquals("V2 Title", merged.title)
        assertEquals("V1 Artist", merged.artist)
        assertEquals("V1 Album", merged.album)
        assertEquals("1990", merged.year)
        assertEquals("Rock", merged.genre)
        assertEquals(5, merged.trackNumber)

        store.writeInPlace(file, TagDraft(title = "New Title"), setOf(TagField.TITLE))

        assertFalse(Mp3File(file).hasId3v1Tag())
        val read = checkNotNull(store.read(file))
        assertEquals("New Title", read.title)
        assertEquals("V1 Artist", read.artist)
        assertEquals("V1 Album", read.album)
        assertEquals("1990", read.year)
        assertEquals("Rock", read.genre)
        assertEquals(5, read.trackNumber)
        assertArrayEquals(originalAudio, audioBytes(file))
    }

    @Test
    fun `a selected blank field clears instead of falling back to v1`() {
        val v1 = Mp3Fixtures.withId3v1(
            audio = Mp3Fixtures.audioOnly(),
            title = "V1 Title",
            artist = "V1 Artist",
            album = "V1 Album",
            year = "1990",
            genre = 17,
        )
        val v2 = ID3v23Tag().also {
            it.padding = true
            it.setTitle("V2 Title")
        }
        val file = taggedFile("v1-clear.mp3", v2.toBytes() + v1)

        store.writeInPlace(file, TagDraft(artist = ""), setOf(TagField.ARTIST))

        val read = checkNotNull(store.read(file))
        assertEquals("V2 Title", read.title)
        assertTrue(read.artist.isBlank())
        assertEquals("V1 Album", read.album)
        assertFalse(Mp3File(file).hasId3v1Tag())
    }

    @Test
    fun `malformed optional frames do not break reading`() {
        val file = taggedFile("malformed-fields.mp3")
        store.writeInPlace(file, TagDraft(title = "Song"), TagField.ALL_FIELDS)
        writeRawTextFrame(file, AbstractID3v2Tag.ID_TRACK, "not-a-number")
        writeRawTextFrame(file, AbstractID3v2Tag.ID_GENRE, "not a genre")

        val read = checkNotNull(store.read(file))
        assertEquals("Song", read.title)
        assertNull(read.trackNumber)
        assertEquals("not a genre", read.genre)
    }

    @Test
    fun `track totals are read written and preserved when only the number changes`() {
        val file = taggedFile("track-total.mp3")
        store.writeInPlace(
            file,
            TagDraft(title = "Song", trackNumber = 3, trackTotal = 12),
            TagField.ALL_FIELDS,
        )
        assertEquals(3, store.read(file)!!.trackNumber)
        assertEquals(12, store.read(file)!!.trackTotal)
        assertEquals("3/12", Mp3File(file).id3v2Tag.track)

        store.writeInPlace(file, TagDraft(trackNumber = 4), setOf(TagField.TRACK_NUMBER))
        assertEquals("4/12", Mp3File(file).id3v2Tag.track)
        assertEquals(12, store.read(file)!!.trackTotal)

        store.writeInPlace(
            file,
            TagDraft(trackNumber = 4, trackTotal = 15),
            setOf(TagField.TRACK_NUMBER),
        )
        assertEquals("4/15", Mp3File(file).id3v2Tag.track)

        store.writeInPlace(file, TagDraft(trackNumber = null), setOf(TagField.TRACK_NUMBER))
        assertNull(store.read(file)!!.trackNumber)
        assertNull(store.read(file)!!.trackTotal)
    }

    @Test
    fun `unknown frames survive a text update`() {
        val file = taggedFile("unknown-frame.mp3")
        store.writeInPlace(file, TagDraft(title = "Before"), TagField.ALL_FIELDS)
        writeRawFrame(file, "TXYZ", byteArrayOf(1, 2, 3, 4))

        store.writeInPlace(file, TagDraft(title = "After"), fields = setOf(TagField.TITLE))

        val mp3 = Mp3File(file)
        assertEquals("After", mp3.id3v2Tag.title)
        assertNotNull(mp3.id3v2Tag.frameSets["TXYZ"])
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            mp3.id3v2Tag.frameSets["TXYZ"]!!.frames.first().data,
        )
    }

    @Test
    fun `artwork is written as a front cover apic frame`() {
        val file = taggedFile("front-cover.mp3")
        store.writeInPlace(
            file,
            TagDraft(
                title = "Song",
                artwork = Artwork(TestImages.jpeg(width = 12, height = 12), "image/jpeg"),
            ),
            TagField.ALL_FIELDS,
        )

        val frames = Mp3File(file).id3v2Tag.frameSets[AbstractID3v2Tag.ID_IMAGE]!!.frames
        val picture = ID3v2PictureFrameData(false, frames.first().data)
        assertEquals(AbstractID3v2Tag.PICTURETYPE_FRONTCOVER.toInt(), picture.pictureType.toInt())
        assertEquals("image/jpeg", picture.mimeType)
    }

    @Test
    fun `title updates leave apic bytes untouched`() {
        val file = taggedFile("apic-title.mp3")
        store.writeInPlace(
            file,
            TagDraft(
                title = "Before",
                artwork = Artwork(TestImages.jpeg(width = 18, height = 18), "image/jpeg"),
            ),
            TagField.ALL_FIELDS,
        )
        appendRawFrame(
            file,
            AbstractID3v2Tag.ID_IMAGE,
            apicPayload("image/png", AbstractID3v2Tag.PICTURETYPE_BACKCOVER, TestImages.png(6, 6)),
        )
        val before = rawApicFrames(file).map { it.data.copyOf() }

        store.writeInPlace(file, TagDraft(title = "After"), setOf(TagField.TITLE))

        val after = rawApicFrames(file)
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (expected, actual) ->
            assertArrayEquals(expected, actual.data)
        }
    }

    @Test
    fun `replacing the front cover keeps other apic pictures`() {
        val file = taggedFile("apic-replace.mp3")
        val backCover = TestImages.png(width = 7, height = 9)
        store.writeInPlace(
            file,
            TagDraft(
                title = "Song",
                artwork = Artwork(TestImages.jpeg(width = 10, height = 10), "image/jpeg"),
            ),
            TagField.ALL_FIELDS,
        )
        appendRawFrame(
            file,
            AbstractID3v2Tag.ID_IMAGE,
            apicPayload("image/png", AbstractID3v2Tag.PICTURETYPE_BACKCOVER, backCover),
        )
        val newFront = TestImages.jpeg(width = 14, height = 14)

        store.writeInPlace(
            file,
            TagDraft(title = "Song", artwork = Artwork(newFront, "image/jpeg")),
            setOf(TagField.TITLE, TagField.ARTWORK),
        )

        val pictures = rawApicFrames(file).map {
            ID3v2PictureFrameData(false, it.data)
        }
        val fronts = pictures.filter {
            it.pictureType == AbstractID3v2Tag.PICTURETYPE_FRONTCOVER
        }
        val backs = pictures.filter {
            it.pictureType == AbstractID3v2Tag.PICTURETYPE_BACKCOVER
        }
        assertEquals(1, fronts.size)
        assertEquals(1, backs.size)
        assertArrayEquals(newFront, fronts.single().imageData)
        assertArrayEquals(backCover, backs.single().imageData)
    }

    @Test
    fun `artwork mime type is derived from the bytes`() {
        val file = taggedFile("mime.mp3")
        val png = TestImages.png(width = 11, height = 11)

        store.writeInPlace(
            file,
            TagDraft(title = "Song", artwork = Artwork(png, "image/jpeg")),
            TagField.ALL_FIELDS,
        )

        assertEquals("image/png", store.read(file)!!.artwork!!.mimeType)
        val picture = ID3v2PictureFrameData(
            false,
            rawApicFrames(file).single().data,
        )
        assertEquals("image/png", picture.mimeType)
    }

    @Test
    fun `unsupported artwork bytes are rejected`() {
        val file = taggedFile("bad-art.mp3")

        assertThrows(IllegalArgumentException::class.java) {
            store.writeInPlace(
                file,
                TagDraft(title = "Song", artwork = Artwork(byteArrayOf(1, 2, 3), "image/jpeg")),
                TagField.ALL_FIELDS,
            )
        }
    }

    @Test
    fun `year is normalized from an iso date and invalid years are rejected`() {
        val file = taggedFile("year.mp3")
        store.writeInPlace(
            file,
            TagDraft(title = "Song", year = " 2022-07-04 "),
            TagField.ALL_FIELDS,
        )
        assertEquals("2022", store.read(file)!!.year)

        assertThrows(IllegalArgumentException::class.java) {
            store.writeInPlace(file, TagDraft(year = "22"), setOf(TagField.YEAR))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.writeInPlace(file, TagDraft(year = "abcd"), setOf(TagField.YEAR))
        }
    }

    @Test
    fun `a real v24 utf8 fixture is read and stays v24 on update`() {
        val file = taggedFile("v24-utf8.mp3")
        writeV24Fixture(file, title = "כותרת מקורית", artist = "אמן מקורי", year = "2019")

        val before = checkNotNull(store.read(file))
        assertEquals("כותרת מקורית", before.title)
        assertEquals("אמן מקורי", before.artist)
        assertEquals("2019", before.year)

        store.writeInPlace(file, TagDraft(title = "כותרת חדשה"), setOf(TagField.TITLE))

        val mp3 = Mp3File(file)
        assertEquals("4.0", mp3.id3v2Tag.version)
        assertEquals("כותרת חדשה", mp3.id3v2Tag.title)
        assertEquals("אמן מקורי", mp3.id3v2Tag.artist)
        assertEquals("2019", store.read(file)!!.year)
    }

    @Test
    fun `writing a year to v24 uses tdrc instead of tyer`() {
        val file = taggedFile("v24-year.mp3")
        writeV24Fixture(file, title = "Song", artist = "Artist", year = "2019")

        store.writeInPlace(file, TagDraft(year = "2021"), setOf(TagField.YEAR))

        val tag = Mp3File(file).id3v2Tag
        assertEquals("4.0", tag.version)
        assertNotNull(tag.frameSets["TDRC"])
        assertNull(tag.frameSets[AbstractID3v2Tag.ID_YEAR])
        assertEquals("2021", store.read(file)!!.year)
    }

    @Test
    fun `v22 tags are rejected without changing the file`() {
        val v22 = ID3v22Tag().also {
            it.padding = true
            it.setTitle("Old")
        }
        val file = taggedFile("v22.mp3", v22.toBytes() + Mp3Fixtures.audioOnly())
        val original = file.readBytes()

        assertThrows(UnsupportedTagVersionException::class.java) {
            store.writeInPlace(file, TagDraft(title = "New"), setOf(TagField.TITLE))
        }
        assertArrayEquals(original, file.readBytes())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `writes keep the audio frames byte for byte`() {
        val file = taggedFile("audio-equal.mp3", Mp3Fixtures.audioOnly(frameCount = 8))
        val before = audioBytes(file)

        store.writeInPlace(
            file,
            TagDraft(
                title = "Song",
                artist = "Artist",
                album = "Album",
                artwork = Artwork(TestImages.jpeg(32, 32), "image/jpeg"),
            ),
            TagField.ALL_FIELDS,
        )

        assertArrayEquals(before, audioBytes(file))
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `save failure leaves the original intact and cleans artifacts`() {
        val file = taggedFile("save-fail.mp3", Mp3Fixtures.audioOnly())
        val original = file.readBytes()
        val failing = Mp3TagStore(
            saver = Mp3TagStore.Mp3Saver { _, _ -> throw IOException("simulated save failure") },
        )

        assertThrows(TagWriteException::class.java) {
            failing.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
        }

        assertArrayEquals(original, file.readBytes())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `move failure leaves the original intact and cleans artifacts`() {
        val file = taggedFile("move-fail.mp3", Mp3Fixtures.audioOnly())
        val original = file.readBytes()
        val failing = Mp3TagStore(
            Mp3TagStore.AtomicMover { _, _ -> throw IOException("simulated move failure") },
        )

        assertThrows(TagWriteException::class.java) {
            failing.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
        }

        assertArrayEquals(original, file.readBytes())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `unsupported atomic move fails without a silent fallback`() {
        val file = taggedFile("atomic-fail.mp3", Mp3Fixtures.audioOnly())
        val original = file.readBytes()
        val failing = Mp3TagStore(
            Mp3TagStore.AtomicMover { source, target ->
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "test")
            },
        )

        assertThrows(TagWriteException::class.java) {
            failing.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
        }

        assertArrayEquals(original, file.readBytes())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `post move verification restores the backup`() {
        val file = taggedFile("verify-fail.mp3", Mp3Fixtures.audioOnly(frameCount = 6))
        val originalAudio = audioBytes(file)
        val corrupting = Mp3TagStore(
            Mp3TagStore.AtomicMover { source, target ->
                Mp3TagStore.DefaultAtomicMover.move(source, target)
                RandomAccessFile(target.toFile(), "rw").use { raf ->
                    val audioStart = Mp3File(target.toFile()).startOffset.toLong()
                    raf.seek(audioStart)
                    val value = raf.read()
                    raf.seek(audioStart)
                    raf.write(value xor 0x01)
                }
            },
        )

        assertThrows(TagWriteException::class.java) {
            corrupting.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
        }

        assertArrayEquals(originalAudio, audioBytes(file))
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `stale temporary files are removed before writing`() {
        val file = taggedFile("stale.mp3", Mp3Fixtures.audioOnly())
        val stale = File(file.parentFile!!, ".cue-tag-${file.name}-stale.tmp")
        stale.writeText("stale")

        store.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)

        assertFalse(stale.exists())
        assertNoArtifacts(file.parentFile!!)
    }

    @Test
    fun `read only parent directory fails without touching the file`() {
        val directory = temporaryFolder.newFolder("readonly")
        val file = taggedFile(directory, "readonly.mp3", Mp3Fixtures.audioOnly())
        val original = file.readBytes()
        val madeReadOnly = directory.setWritable(false)
        try {
            Assume.assumeTrue(
                "filesystem does not enforce read-only directories",
                madeReadOnly && !directory.canWrite(),
            )
            assertThrows(TagWriteException::class.java) {
                store.writeInPlace(file, TagDraft(title = "New"), TagField.ALL_FIELDS)
            }
            assertArrayEquals(original, file.readBytes())
        } finally {
            directory.setWritable(true)
        }
    }

    @Test
    fun `text fields exclude artwork`() {
        assertFalse(TagField.ARTWORK in TagField.TEXT_FIELDS)
        assertTrue(TagField.TITLE in TagField.TEXT_FIELDS)
        assertEquals(TagField.entries.toSet(), TagField.ALL_FIELDS)
    }

    private fun taggedFile(
        name: String,
        bytes: ByteArray = Mp3Fixtures.audioOnly(),
    ): File = taggedFile(temporaryFolder.root, name, bytes)

    private fun taggedFile(directory: File, name: String, bytes: ByteArray): File =
        File(directory, name).also { it.writeBytes(bytes) }

    private fun writeV24Fixture(file: File, title: String, artist: String, year: String) {
        val tag = ID3v24Tag().also { it.padding = true }
        tag.frameSets[AbstractID3v2Tag.ID_TITLE] = frameSet(
            AbstractID3v2Tag.ID_TITLE,
            utf8Payload(title),
        )
        tag.frameSets[AbstractID3v2Tag.ID_ARTIST] = frameSet(
            AbstractID3v2Tag.ID_ARTIST,
            utf8Payload(artist),
        )
        tag.frameSets["TDRC"] = frameSet("TDRC", utf8Payload(year))
        file.writeBytes(tag.toBytes() + Mp3Fixtures.audioOnly())
    }

    private fun frameSet(frameId: String, payload: ByteArray): ID3v2FrameSet =
        ID3v2FrameSet(frameId).also { it.addFrame(ID3v2Frame(frameId, payload)) }

    private fun utf8Payload(value: String): ByteArray =
        byteArrayOf(EncodedText.TEXT_ENCODING_UTF_8) + value.toByteArray(Charsets.UTF_8)

    private fun writeRawTextFrame(file: File, frameId: String, value: String) {
        val payload = byteArrayOf(EncodedText.TEXT_ENCODING_ISO_8859_1) +
            value.toByteArray(Charsets.ISO_8859_1)
        writeRawFrame(file, frameId, payload)
    }

    private fun writeRawFrame(file: File, frameId: String, payload: ByteArray) {
        val mp3 = Mp3File(file)
        val tag = mp3.id3v2Tag ?: ID3v23Tag()
        tag.frameSets[frameId] = frameSet(frameId, payload)
        invalidateLength(tag)
        mp3.id3v2Tag = tag
        saveReplacing(mp3, file)
    }

    private fun appendRawFrame(file: File, frameId: String, payload: ByteArray) {
        val mp3 = Mp3File(file)
        val tag = mp3.id3v2Tag ?: ID3v23Tag()
        val frames = tag.frameSets[frameId]
            ?: ID3v2FrameSet(frameId).also { tag.frameSets[frameId] = it }
        frames.addFrame(ID3v2Frame(frameId, payload))
        invalidateLength(tag)
        mp3.id3v2Tag = tag
        saveReplacing(mp3, file)
    }

    private fun invalidateLength(tag: com.mpatric.mp3agic.ID3v2) {
        val padding = tag.padding
        tag.padding = !padding
        tag.padding = padding
    }

    private fun rawApicFrames(file: File): List<ID3v2Frame> =
        Mp3File(file).id3v2Tag.frameSets[AbstractID3v2Tag.ID_IMAGE]?.frames.orEmpty()

    private fun apicPayload(mimeType: String, pictureType: Byte, image: ByteArray): ByteArray =
        byteArrayOf(EncodedText.TEXT_ENCODING_ISO_8859_1) +
            mimeType.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, pictureType, 0) +
            image

    private fun audioBytes(file: File): ByteArray {
        val mp3 = Mp3File(file)
        val length = mp3.endOffset - mp3.startOffset + 1
        val bytes = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(mp3.startOffset.toLong())
            raf.readFully(bytes)
        }
        return bytes
    }

    private fun assertNoArtifacts(directory: File) {
        val artifacts = directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(".cue-tag-") }
        assertTrue("leftover artifacts: ${artifacts.map { it.name }}", artifacts.isEmpty())
    }

    private fun saveReplacing(mp3: Mp3File, file: File) {
        val temporary = File(file.parentFile!!, "tmp-${file.name}")
        try {
            mp3.save(temporary.path)
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }
}
