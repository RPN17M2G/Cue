package io.github.rpn17m2g.cue.tags

/** The file could not be parsed as an MP3 or its tag could not be read. */
class TagReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The ID3 tag could not be written, verified, or atomically swapped into place. */
open class TagWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The file carries an ID3 version this writer deliberately refuses to rewrite. */
class UnsupportedTagVersionException(
    val version: String,
    message: String,
) : TagWriteException(message)
