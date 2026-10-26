package io.github.rpn17m2g.cue.data.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.ocr.OcrEngine
import io.github.rpn17m2g.cue.domain.ocr.OcrFrame
import io.github.rpn17m2g.cue.domain.ocr.OcrFrameText
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** ML Kit Text Recognition v2 with the bundled Latin model. */
class MlKitOcrEngine(private val context: Context) : OcrEngine {

    override val source: IdentificationSource = IdentificationSource.OCR_LATIN

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText> =
        frames.map { frame ->
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(frame.file))
                val result = recognizer.process(image).awaitResult()
                val lines = result.textBlocks
                    .flatMap { block -> block.lines }
                    .map { line -> line.text.trim() }
                    .filter { it.isNotEmpty() }
                OcrFrameText(frame.timestampMs, lines)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OcrFrameText(frame.timestampMs, emptyList())
            }
        }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }
}
