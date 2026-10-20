package io.github.rpn17m2g.cue.tags

import java.io.File

/**
 * Synthetic MPEG-1 Layer III frames (128 kbps, 44.1 kHz, stereo, silent payload).
 * mp3agic only validates headers, so zeroed frame bodies are sufficient and keep
 * fixtures small and deterministic.
 */
object Mp3Fixtures {

    private const val FRAME_LENGTH = 417
    private val FRAME_HEADER = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)

    fun audioOnly(frameCount: Int = 4): ByteArray {
        require(frameCount > 0) { "frameCount must be positive" }
        val audio = ByteArray(frameCount * FRAME_LENGTH)
        repeat(frameCount) { index ->
            FRAME_HEADER.copyInto(audio, destinationOffset = index * FRAME_LENGTH)
        }
        return audio
    }

    fun withId3v1(
        audio: ByteArray,
        title: String,
        artist: String,
        album: String,
        year: String,
        genre: Int,
        trackNumber: Int? = null,
    ): ByteArray {
        val tag = ByteArray(ID3V1_LENGTH)
        tag[0] = 'T'.code.toByte()
        tag[1] = 'A'.code.toByte()
        tag[2] = 'G'.code.toByte()
        copyPadded(title, tag, 3, 30)
        copyPadded(artist, tag, 33, 30)
        copyPadded(album, tag, 63, 30)
        copyPadded(year, tag, 93, 4)
        if (trackNumber != null) {
            tag[125] = 0
            tag[126] = trackNumber.toByte()
        }
        tag[127] = genre.toByte()
        return audio + tag
    }

    fun write(file: File, bytes: ByteArray = audioOnly()): File {
        file.writeBytes(bytes)
        return file
    }

    private fun copyPadded(value: String, target: ByteArray, offset: Int, length: Int) {
        val encoded = value.toByteArray(Charsets.ISO_8859_1)
        val count = minOf(encoded.size, length)
        encoded.copyInto(target, destinationOffset = offset, endIndex = count)
    }

    private const val ID3V1_LENGTH = 128
}
