package io.github.rpn17m2g.cue.tags

/** The file could not be parsed as an MP3 or its tag could not be read. */
class TagReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The ID3 tag could not be written without risking the audio data. */
class TagWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)
