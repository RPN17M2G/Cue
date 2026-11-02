package io.github.rpn17m2g.cue.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The values a palette contributes that Material 3 has no slot for. Screens
 * read these from [LocalCueTokens] and never branch on the palette itself.
 */
@Immutable
data class CueTokens(
    val style: CueStyle,
    val heroBrush: Brush,
    val glow: Color,
    val cardShape: RoundedCornerShape,
    val thumbnailShape: RoundedCornerShape,
    val buttonShape: RoundedCornerShape,
    val fieldShape: RoundedCornerShape,
    val sheetShape: RoundedCornerShape,
    val shadowElevation: Dp,
    val titleWeight: FontWeight,
    val bodyWeight: FontWeight,
    val titleTracking: TextUnit,
    /** Text drawn over artwork or the hero gradient. */
    val heroInk: Color,
    val success: Color,
    val caution: Color,
)

val LocalCueTokens = staticCompositionLocalOf<CueTokens> {
    error("CueTokens is only available inside CueTheme")
}

internal fun cueShapes(style: CueStyle): Shapes = when (style) {
    CueStyle.CHEERFUL -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CueStyle.PROFESSIONAL -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
}

internal fun cueTokens(spec: PaletteSpec, dark: Boolean): CueTokens {
    val cheerful = spec.style == CueStyle.CHEERFUL
    return CueTokens(
        style = spec.style,
        heroBrush = Brush.linearGradient(spec.heroGradient),
        glow = spec.glow,
        cardShape = RoundedCornerShape(if (cheerful) 22.dp else 12.dp),
        thumbnailShape = RoundedCornerShape(if (cheerful) 18.dp else 10.dp),
        buttonShape = if (cheerful) {
            RoundedCornerShape(percent = 50)
        } else {
            RoundedCornerShape(12.dp)
        },
        fieldShape = RoundedCornerShape(if (cheerful) 14.dp else 10.dp),
        sheetShape = RoundedCornerShape(
            topStart = if (cheerful) 28.dp else 16.dp,
            topEnd = if (cheerful) 28.dp else 16.dp,
        ),
        shadowElevation = if (cheerful) 8.dp else 1.dp,
        titleWeight = if (cheerful) FontWeight.ExtraBold else FontWeight.SemiBold,
        bodyWeight = if (cheerful) FontWeight.Medium else FontWeight.Normal,
        titleTracking = if (cheerful) (-0.02).em else (-0.01).em,
        heroInk = Color.White,
        success = if (dark) spec.successDark else spec.successLight,
        caution = if (dark) spec.cautionDark else spec.cautionLight,
    )
}
