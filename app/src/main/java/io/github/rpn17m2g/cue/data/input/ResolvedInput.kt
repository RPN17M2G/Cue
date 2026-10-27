package io.github.rpn17m2g.cue.data.input

enum class InputKind {
    VIDEO,
    MP3,
}

/**
 * A picker/share input copied into the app cache. [path] is always a local file;
 * [uri] identifies the source document for in-place write-back.
 *
 * [canEditInPlace] is true only when the source grants persistent write access
 * (SAF) or is a MediaStore row this app may update.
 */
data class ResolvedInput(
    val id: String,
    val uri: String,
    val path: String,
    val displayName: String,
    val mimeType: String?,
    val kind: InputKind,
    val durationMs: Long?,
    val canEditInPlace: Boolean = false,
)

/** Decides whether an input goes through the conversion or the MP3 flow. */
object InputKindDetector {

    private const val MP3_MIME = "audio/mpeg"
    private const val MP3_MIME_ALT = "audio/mp3"

    fun detect(mimeType: String?, displayName: String?, sniff: ByteArray?): InputKind {
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        if (mime == MP3_MIME || mime == MP3_MIME_ALT) return InputKind.MP3
        if (displayName?.substringAfterLast('.', "")?.lowercase() == "mp3") return InputKind.MP3
        if (sniff != null && looksLikeMp3(sniff)) return InputKind.MP3
        return InputKind.VIDEO
    }

    private fun looksLikeMp3(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 &&
            bytes[0].toInt() == 'I'.code &&
            bytes[1].toInt() == 'D'.code &&
            bytes[2].toInt() == '3'.code
        ) {
            return true
        }
        if (bytes.size < 2) return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 0xFF && (second and 0xE0) == 0xE0
    }
}
