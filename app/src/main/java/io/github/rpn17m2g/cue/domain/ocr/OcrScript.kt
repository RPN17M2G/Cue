package io.github.rpn17m2g.cue.domain.ocr

/**
 * Which reading the user asked for when a first scan picked the wrong script.
 *
 * [AUTO] lets the scores and the script found on screen decide, which is what a
 * normal identification uses. The explicit values re-read the same frames with
 * one engine only: a Hebrew song shown in an English app can come back as Latin
 * gibberish, and the user is the one who knows which script is on screen.
 */
enum class OcrScript {
    AUTO,
    LATIN,
    HEBREW,
}
