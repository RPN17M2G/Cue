package io.github.rpn17m2g.cue.data.input

enum class InputKind {
    VIDEO,
    MP3,
}

/**
 * A picker/share input copied into the app cache. [path] is always a local file;
 * [uri] is kept only for diagnostics and for re-reading the source.
 */
data class ResolvedInput(
    val id: String,
    val uri: String,
    val path: String,
    val displayName: String,
    val mimeType: String?,
    val kind: InputKind,
    val durationMs: Long?,
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
