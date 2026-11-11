package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.data.input.InputKind
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.ocr.DualOcrRecognizer
import io.github.rpn17m2g.cue.domain.ocr.FrameExtractor
import io.github.rpn17m2g.cue.domain.ocr.FrameSampler
import io.github.rpn17m2g.cue.domain.ocr.OcrCandidate
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.AcoustIdKey
import io.github.rpn17m2g.cue.tags.TagStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SongIdentification(
    val outcome: IdentificationOutcome,
    val ocrFailed: Boolean,
    val hebrewOcrUsed: Boolean,
)

/**
 * Runs the identification stages for one input: embedded tags for MP3, or
 * sampled-frame OCR for video, followed by the online cascade. Frames are
 * temporary and are removed whether recognition succeeds, fails, or is
 * cancelled.
 */
class IdentifySongUseCase(
    private val tagStore: TagStore,
    private val frameExtractor: FrameExtractor,
    private val recognizer: DualOcrRecognizer,
    private val framesRoot: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun identify(
        input: ResolvedInput,
        embeddedTags: TagDraft?,
        settings: AppSettings,
        preferHebrew: Boolean,
        cascade: IdentificationCascade,
        forceFingerprint: Boolean = false,
        manualQuery: LookupSeed? = null,
    ): SongIdentification = withContext(ioDispatcher) {
        val embedded = embeddedTags ?: readEmbeddedTags(input)
        var candidates: List<OcrCandidate> = emptyList()
        var rawLines: List<String> = emptyList()
        var trackDurationMs: Long? = null
        var ocrFailed = false
        var hebrewUsed = false

        // A manual search uses the text the user already corrected, so there
        // is nothing to read from the frames and no reason to spend minutes
        // running OCR again.
        if (input.kind == InputKind.VIDEO && manualQuery == null) {
            val framesDir = File(framesRoot, UUID.randomUUID().toString())
            try {
                val timestamps = FrameSampler.timestamps(
                    durationMs = input.durationMs,
                    scanWindowSeconds = settings.scanWindowSeconds,
                    intervalMs = settings.ocrIntervalMs,
                )
                val frames = frameExtractor.extract(File(input.path), timestamps, framesDir)
                if (frames.isEmpty()) {
                    ocrFailed = true
                } else {
                    val recognition = recognizer.recognize(frames, preferHebrew)
                    candidates = recognition.candidates
                    rawLines = recognition.rawLines
                    trackDurationMs = recognition.trackDurationMs
                    ocrFailed = recognition.failed
                    hebrewUsed = recognition.hebrewUsed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ocrFailed = true
            } finally {
                framesDir.deleteRecursively()
            }
        }

        val outcome = cascade.identify(
            ocrCandidates = candidates,
            embeddedTags = embedded,
            networkEnabled = settings.networkLookups,
            fingerprintEnabled = settings.fingerprintEnabled,
            acoustIdConfigured = AcoustIdKey.isPlausible(settings.acoustIdKey),
            rawOcrLines = rawLines,
            durationMs = input.durationMs,
            trackDurationMs = trackDurationMs,
            forceFingerprint = forceFingerprint,
            manualQuery = manualQuery,
        )
        SongIdentification(outcome, ocrFailed, hebrewUsed)
    }

    private fun readEmbeddedTags(input: ResolvedInput): TagDraft? {
        if (input.kind != InputKind.MP3) return null
        return runCatching { tagStore.read(File(input.path)) }.getOrNull()
    }
}
