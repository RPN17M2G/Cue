package io.github.rpn17m2g.cue.data.input

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Extracts the shared/viewed document from the intents Cue accepts. */
object InputIntentParser {

    fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    fun extractOpenTarget(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_OPEN_TARGET)

    const val EXTRA_OPEN_TARGET = "io.github.rpn17m2g.cue.OPEN_TARGET"
    const val EXTRA_OPEN_INPUT_ID = "io.github.rpn17m2g.cue.OPEN_INPUT_ID"
    const val OPEN_RESULT = "result"
    const val OPEN_ERROR = "error"
    const val OPEN_PROCESSING = "processing"
}
