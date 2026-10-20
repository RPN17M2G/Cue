package io.github.rpn17m2g.cue.tags

import com.mpatric.mp3agic.AbstractID3v2Tag
import com.mpatric.mp3agic.EncodedText
import com.mpatric.mp3agic.ID3v23Tag
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.ID3v2Frame
import com.mpatric.mp3agic.ID3v2FrameSet
import com.mpatric.mp3agic.ID3v2PictureFrameData
import com.mpatric.mp3agic.Mp3File
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun `round trip keeps every field including hebrew and artwork`() {
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
            artwork = Artwork(artworkBytes, "image/png"),
        )

        store.writeInPlace(file, draft)

        val read = checkNotNull(store.read(file))
        assertEquals("שלום עולם", read.title)
        assertEquals("אמן אלמוני", read.artist)
        assertEquals("אלבום ראשון", read.album)
        assertEquals("Various Artists", read.albumArtist)
        assertEquals("2024", read.year)
        assertEquals("Rock", read.genre)
        assertEquals(7, read.trackNumber)
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
                artwork = Artwork(artworkBytes, "image/png"),
            ),
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
        )

        store.writeInPlace(file, TagDraft(title = "Song Only"), fields = setOf(TagField.TITLE))
        assertNotNull(store.read(file)!!.artwork)

        store.writeInPlace(file, TagDraft(title = "Song Only", artwork = null), fields = setOf(TagField.ARTWORK))
        assertNull(store.read(file)!!.artwork)
    }

    @Test
    fun `empty text fields remove their frames`() {
        val file = taggedFile("empty.mp3")
        store.writeInPlace(file, TagDraft(title = "Song", artist = "Artist", genre = "Rock"))

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

        store.writeInPlace(file, TagDraft(title = "Fresh", artist = "New", album = "Tag"))

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
    fun `malformed optional frames do not break reading`() {
        val file = taggedFile("malformed-fields.mp3")
        store.writeInPlace(file, TagDraft(title = "Song"))
        writeRawTextFrame(file, AbstractID3v2Tag.ID_TRACK, "not-a-number")
        writeRawTextFrame(file, AbstractID3v2Tag.ID_GENRE, "not a genre")

        val read = checkNotNull(store.read(file))
        assertEquals("Song", read.title)
        assertNull(read.trackNumber)
        assertEquals("not a genre", read.genre)
    }

    @Test
    fun `track numbers with totals are read as the leading number`() {
        val file = taggedFile("track-total.mp3")
        store.writeInPlace(file, TagDraft(title = "Song"))
        writeRawTextFrame(file, AbstractID3v2Tag.ID_TRACK, "12/24")

        assertEquals(12, store.read(file)!!.trackNumber)
    }

    @Test
    fun `unknown frames survive a text update`() {
        val file = taggedFile("unknown-frame.mp3")
        store.writeInPlace(file, TagDraft(title = "Before"))
        writeRawFrame(file, "TXYZ", byteArrayOf(1, 2, 3, 4))

        store.writeInPlace(file, TagDraft(title = "After"), fields = setOf(TagField.TITLE))

        val mp3 = Mp3File(file)
        assertEquals("After", mp3.id3v2Tag.title)
        assertNotNull(mp3.id3v2Tag.frameSets["TXYZ"])
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), mp3.id3v2Tag.frameSets["TXYZ"]!!.frames.first().data)
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
        )

        val frames = Mp3File(file).id3v2Tag.frameSets[AbstractID3v2Tag.ID_IMAGE]!!.frames
        val picture = ID3v2PictureFrameData(false, frames.first().data)
        assertEquals(AbstractID3v2Tag.PICTURETYPE_FRONTCOVER.toInt(), picture.pictureType.toInt())
        assertEquals("image/jpeg", picture.mimeType)
    }

    @Test
    fun `v24 TDRC is used when no v23 year frame exists`() {
        val file = taggedFile("v24-year.mp3")
        store.writeInPlace(file, TagDraft(title = "Song", year = "2019"))

        val mp3 = Mp3File(file)
        val yearFrame = mp3.id3v2Tag.frameSets[AbstractID3v2Tag.ID_YEAR]!!.frames.first()
        val v24 = ID3v24Tag()
        mp3.id3v2Tag.frameSets.forEach { (frameId, frameSet) ->
            if (frameId != AbstractID3v2Tag.ID_YEAR) {
                v24.frameSets[frameId] = frameSet
            }
        }
        v24.frameSets["TDRC"] = ID3v2FrameSet("TDRC").also {
            it.addFrame(ID3v2Frame("TDRC", yearFrame.data))
        }
        mp3.id3v2Tag = v24
        saveReplacing(mp3, file)

        val read = checkNotNull(store.read(file))
        assertEquals("2019", read.year)
    }

    @Test
    fun `an existing v24 tag is rewritten as v23 and keeps untouched fields`() {
        val file = taggedFile("v24.mp3")
        store.writeInPlace(file, TagDraft(title = "Before", artist = "Artist", album = "Album"))
        convertToV24(file)

        store.writeInPlace(file, TagDraft(title = "After"), fields = setOf(TagField.TITLE))

        val mp3 = Mp3File(file)
        assertEquals("3.0", mp3.id3v2Tag.version)
        assertEquals("After", mp3.id3v2Tag.title)
        assertEquals("Artist", mp3.id3v2Tag.artist)
        assertEquals("Album", mp3.id3v2Tag.album)
    }

    private fun taggedFile(name: String, bytes: ByteArray = Mp3Fixtures.audioOnly()): File =
        temporaryFolder.newFile(name).also { it.writeBytes(bytes) }

    private fun writeRawTextFrame(file: File, frameId: String, value: String) {
        val payload = byteArrayOf(EncodedText.TEXT_ENCODING_ISO_8859_1) +
            value.toByteArray(Charsets.ISO_8859_1)
        writeRawFrame(file, frameId, payload)
    }

    private fun writeRawFrame(file: File, frameId: String, payload: ByteArray) {
        val mp3 = Mp3File(file)
        val tag = mp3.id3v2Tag ?: ID3v23Tag()
        tag.frameSets[frameId] = ID3v2FrameSet(frameId).also {
            it.addFrame(ID3v2Frame(frameId, payload))
        }
        val padding = tag.padding
        tag.padding = !padding
        tag.padding = padding
        mp3.id3v2Tag = tag
        saveReplacing(mp3, file)
    }

    private fun convertToV24(file: File) {
        val mp3 = Mp3File(file)
        val v24 = ID3v24Tag()
        v24.frameSets.putAll(mp3.id3v2Tag.frameSets)
        mp3.id3v2Tag = v24
        saveReplacing(mp3, file)
    }

    private fun saveReplacing(mp3: Mp3File, file: File) {
        val temporary = File(file.parentFile, "tmp-${file.name}")
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
