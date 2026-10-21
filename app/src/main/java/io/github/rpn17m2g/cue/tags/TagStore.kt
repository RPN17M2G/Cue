package io.github.rpn17m2g.cue.tags

import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.io.File

/**
 * Tag access for a local MP3 [File].
 *
 * This interface does not understand SAF/ContentResolver URIs. The Android layer must
 * copy a picked SAF document into the app cache, edit that local copy, verify the audio
 * payload survived, and then write the result back through ContentResolver. That last
 * step cannot be atomic for an arbitrary provider: keep a backup of the original
 * document and define an explicit partial-write policy (for example, only replace the
 * document after the edited copy has been fully verified).
 */
interface TagStore {

    /** Returns `null` when the file has neither an ID3v2 nor an ID3v1 tag. */
    fun read(file: File): TagDraft?

    /**
     * Updates only [fields]. A blank text value removes its frame. Artwork is left
     * untouched unless [TagField.ARTWORK] is present.
     *
     * @throws TagReadException when [file] is not a readable MP3.
     * @throws UnsupportedTagVersionException when the existing tag is not v2.3/v2.4.
     * @throws TagWriteException when the write, verification, or atomic swap fails.
     */
    fun writeInPlace(file: File, tag: TagDraft, fields: Set<TagField>)
}
