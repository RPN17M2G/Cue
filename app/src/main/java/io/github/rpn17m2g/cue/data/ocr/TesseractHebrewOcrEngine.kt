package io.github.rpn17m2g.cue.data.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.ocr.OcrEngine
import io.github.rpn17m2g.cue.domain.ocr.OcrFrame
import io.github.rpn17m2g.cue.domain.ocr.OcrFrameText
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
        val text = try {
            tess.setImage(frame.file)
            tess.getUTF8Text().orEmpty()
        } catch (_: LinkageError) {
            ""
        } catch (_: Exception) {
            ""
        }
        val lines = text.lineSequence()
            .map { line -> line.replace(NBSP, " ").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return OcrFrameText(frame.timestampMs, lines)
    }

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
    }
}
