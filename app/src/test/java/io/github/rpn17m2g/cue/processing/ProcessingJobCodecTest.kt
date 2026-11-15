package io.github.rpn17m2g.cue.processing

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TagDraft
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The service never receives artwork bytes directly (a cover can exceed the
 * Binder limit), so the staged-file hand-off is the only thing keeping the
 * picked cover attached to the job. These tests pin that hand-off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProcessingJobCodecTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `artwork survives the trip through the staged file`() {
        val bytes = ByteArray(64) { it.toByte() }
        val intent = Intent()

        ProcessingJobCodec.write(context, intent, job(Artwork(bytes, "image/png")))
        val restored = ProcessingJobCodec.read(intent)

        assertNotNull(restored)
        val artwork = restored!!.draft.artwork
        assertNotNull(artwork)
        assertArrayEquals(bytes, artwork!!.bytes)
        assertEquals("image/png", artwork.mimeType)
        assertTrue(restored.deleteSource)
        ProcessingJobCodec.cleanup(intent)
    }

    @Test
    fun `a job without artwork stays without artwork`() {
        val intent = Intent()

        ProcessingJobCodec.write(context, intent, job(artwork = null))

        assertNull(ProcessingJobCodec.read(intent)?.draft?.artwork)
    }

    @Test
    fun `cleanup removes the staged cover`() {
        val directory = File(context.cacheDir, "processing-jobs")
        val before = directory.listFiles().orEmpty().mapTo(HashSet()) { it.name }
        val intent = Intent()

        ProcessingJobCodec.write(context, intent, job(Artwork(byteArrayOf(1, 2, 3))))
        val created = directory.listFiles().orEmpty().filter { it.name !in before }

        assertEquals(1, created.size)
        ProcessingJobCodec.cleanup(intent)
        assertFalse(created.single().exists())
    }

    @Test
    fun `enrichment fields survive the trip through the intent`() {
        val intent = Intent()
        val enriched = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            lyrics = "Like the legend of the phoenix\nAll ends with beginnings",
            bpm = 116,
            isrc = "GBUM70900217",
            label = "Columbia",
            replayGainDb = -7.4,
            recordingMbid = "rec-1",
            releaseMbid = "release-1",
            artistMbid = "artist-1",
        )

        ProcessingJobCodec.write(context, intent, job(artwork = null, draft = enriched))
        val restored = ProcessingJobCodec.read(intent)

        assertEquals(enriched, restored?.draft)
    }

    @Test
    fun `a draft without enrichment stays without enrichment`() {
        val intent = Intent()

        ProcessingJobCodec.write(context, intent, job(artwork = null))
        val restored = ProcessingJobCodec.read(intent)?.draft

        assertNull(restored?.lyrics)
        assertNull(restored?.bpm)
        assertNull(restored?.isrc)
        assertNull(restored?.label)
        assertNull(restored?.replayGainDb)
        assertNull(restored?.recordingMbid)
        assertNull(restored?.releaseMbid)
        assertNull(restored?.artistMbid)
    }

    private fun job(
        artwork: Artwork?,
        draft: TagDraft = TagDraft(
            title = "Get Lucky",
            artist = "Daft Punk",
            artwork = artwork,
        ),
    ): ProcessingJob = ProcessingJob(
        input = ResolvedInput(
            id = "input",
            uri = "content://input",
            path = "/tmp/input.mp4",
            displayName = "input.mp4",
            mimeType = "video/mp4",
            kind = InputKind.VIDEO,
            durationMs = 10_000,
        ),
        draft = draft,
        trim = null,
        quality = QualityPreset.V0,
        outputFolder = "Music/Cue",
        filenameTemplate = "{artist} - {title}",
        deleteSource = true,
    )
}
