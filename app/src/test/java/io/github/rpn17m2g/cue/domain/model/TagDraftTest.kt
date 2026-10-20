package io.github.rpn17m2g.cue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDraftTest {

    @Test
    fun `hasCoreTags requires title artist and album`() {
        assertFalse(TagDraft(title = "Song", artist = "Artist").hasCoreTags)
        assertTrue(TagDraft(title = "Song", artist = "Artist", album = "Album").hasCoreTags)
    }

    @Test
    fun `estimateMp3Bytes is proportional to duration and bitrate`() {
        val tenSeconds = estimateMp3Bytes(durationMs = 10_000, preset = QualityPreset.CBR_192)
        val twentySeconds = estimateMp3Bytes(durationMs = 20_000, preset = QualityPreset.CBR_192)
        assertEquals(twentySeconds, tenSeconds * 2)
        assertEquals(240_000, tenSeconds)
    }

    @Test
    fun `estimateMp3Bytes returns zero for non-positive durations`() {
        assertEquals(0, estimateMp3Bytes(durationMs = 0, preset = QualityPreset.V0))
        assertEquals(0, estimateMp3Bytes(durationMs = -5, preset = QualityPreset.V0))
    }
}
