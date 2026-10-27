package io.github.rpn17m2g.cue.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.ocr.OcrBox
import io.github.rpn17m2g.cue.domain.ocr.OcrEngine
import io.github.rpn17m2g.cue.domain.ocr.OcrFrame
import io.github.rpn17m2g.cue.domain.ocr.OcrFrameText
import io.github.rpn17m2g.cue.domain.ocr.OcrLine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tesseract second pass for Hebrew, whose script ML Kit's text recognizer does
 * not support. The traineddata travels in assets and is unpacked into app-private
 * storage once, because Tesseract cannot read the APK's compressed assets.
 *
 * TessBaseAPI is not thread-safe, so recognition is serialized and the native
 * instance is recycled after every batch.
 */
class TesseractHebrewOcrEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OcrEngine {

    override val source: IdentificationSource = IdentificationSource.OCR_HEBREW

    private val mutex = Mutex()

    override suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText> {
        if (frames.isEmpty()) return emptyList()
        return mutex.withLock {
            withContext(ioDispatcher) {
                val dataRoot = installTrainedData() ?: return@withContext emptyList()
                val tess = try {
                    TessBaseAPI()
                } catch (_: LinkageError) {
                    return@withContext emptyList()
                }
                try {
                    if (!tess.init(dataRoot.absolutePath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
                        return@withContext emptyList()
                    }
                    tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
                    tess.setVariable(PRESERVE_INTERWORD_SPACES, "1")
                    frames.map { frame -> readFrame(tess, frame) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: LinkageError) {
                    // Native libraries are absent or unaligned for this ABI.
                    emptyList()
                } catch (_: Exception) {
                    emptyList()
                } finally {
                    runCatching { tess.recycle() }
                }
            }
        }
    }

    private fun readFrame(tess: TessBaseAPI, frame: OcrFrame): OcrFrameText {
        val size = imageSize(frame.file)
        val text = try {
            tess.setImage(frame.file)
            tess.getUTF8Text().orEmpty()
        } catch (_: LinkageError) {
            ""
        } catch (_: Exception) {
            ""
        }
        val boxed = readBoxedLines(tess, size)
        val lines = if (boxed.isNotEmpty()) {
            boxed
        } else {
            text.lineSequence()
                .map { line -> line.replace(NBSP, " ").trim() }
                .filter { it.isNotEmpty() }
                .map(::OcrLine)
                .toList()
        }
        return OcrFrameText(frame.timestampMs, lines)
    }

    /**
     * Tesseract reports a box and a confidence per text line. They decide which
     * of its readings is the big, steady title, so they are worth reading even
     * though the plain text already contains the words.
     *
     * Lines it does not actually trust are dropped. This engine only has Hebrew
     * traineddata, so Latin text on screen is forced into Hebrew letters and
     * comes back with a confidence near zero ("Believer" became "ובבובן-" at
     * 0). Keeping those guesses would let the Hebrew pass replace a perfect
     * Latin reading just because the app UI is Hebrew.
     */
    private fun readBoxedLines(tess: TessBaseAPI, size: ImageSize): List<OcrLine> = try {
        val iterator = tess.resultIterator
        if (iterator == null) {
            emptyList()
        } else {
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val lines = mutableListOf<OcrLine>()
            try {
                iterator.begin()
                do {
                    val text = iterator.getUTF8Text(level)
                        ?.replace(NBSP, " ")
                        ?.trim()
                        .orEmpty()
                    val confidence = iterator.confidence(level)
                    if (text.isNotEmpty() && confidence >= MIN_TRUSTED_CONFIDENCE) {
                        lines += OcrLine(
                            text = text,
                            box = iterator.getBoundingRect(level)?.toOcrBox(size),
                            confidence = confidence,
                        )
                    }
                } while (iterator.next(level))
            } finally {
                runCatching { iterator.delete() }
            }
            lines
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** Reads the cached frame's pixel size without decoding it. */
    private fun imageSize(file: File): ImageSize = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        ImageSize(
            width = options.outWidth.toFloat().coerceAtLeast(1f),
            height = options.outHeight.toFloat().coerceAtLeast(1f),
        )
    } catch (_: Throwable) {
        ImageSize(1f, 1f)
    }

    private fun android.graphics.Rect.toOcrBox(size: ImageSize): OcrBox = OcrBox(
        left = (left / size.width).coerceIn(0f, 1f),
        top = (top / size.height).coerceIn(0f, 1f),
        right = (right / size.width).coerceIn(0f, 1f),
        bottom = (bottom / size.height).coerceIn(0f, 1f),
    )

    private data class ImageSize(val width: Float, val height: Float)

    private fun installTrainedData(): File? {
        val dataRoot = File(context.filesDir, DATA_DIRECTORY)
        val target = File(File(dataRoot, TESSDATA_DIRECTORY), "$LANGUAGE.traineddata")
        if (target.length() > 0L) return dataRoot

        val temporary = File(target.parentFile, "${target.name}.tmp")
        return try {
            target.parentFile?.mkdirs()
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            dataRoot.takeIf { target.length() > 0L }
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private companion object {
        const val LANGUAGE = "heb"
        const val DATA_DIRECTORY = "tesseract"
        const val TESSDATA_DIRECTORY = "tessdata"
        const val ASSET_PATH = "tessdata/heb.traineddata"
        const val PRESERVE_INTERWORD_SPACES = "preserve_interword_spaces"
        const val NBSP = "\u00A0"

        /** Tesseract reports 0-100; only trusted lines are worth ranking. */
        const val MIN_TRUSTED_CONFIDENCE = 60f
    }
}
