package io.github.rpn17m2g.cue.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.rpn17m2g.cue.R

/**
 * Rubik carries both Latin and Hebrew, so the app has one voice in either
 * language. The five static instances are cut from the variable font so every
 * weight renders exactly, including on API 31 where variable axes are not
 * exposed through Compose's [Font].
 */
val CueFontFamily = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_extrabold, FontWeight.ExtraBold),
)
