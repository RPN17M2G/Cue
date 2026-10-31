package io.github.rpn17m2g.cue.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.rpn17m2g.cue.settings.CuePalette

/**
 * The shape and type personality a palette carries. Cheerful palettes are
 * round and heavy; professional ones are tight and flat. Kept separate from
 * [CuePalette] only so the tokens can be built from one value.
 */
enum class CueStyle { CHEERFUL, PROFESSIONAL }

/**
 * One palette, fully resolved: both colour schemes plus the two values that do
 * not map onto a Material slot (the placeholder-artwork gradient and the
 * shadow glow).
 *
 * Everything here is hand-specified. Material's generated dark schemes
 * desaturate greens and blues badly, so each dark variant is tuned for hue and
 * contrast instead (ADR 0007).
 */
@Immutable
data class PaletteSpec(
    val palette: CuePalette,
    val style: CueStyle,
    val light: ColorScheme,
    val dark: ColorScheme,
    val heroGradient: List<Color>,
    val glow: Color,
)

private val ErrorLight = Color(0xFFB3261E)
private val ErrorLightContainer = Color(0xFFF9DEDC)
private val OnErrorLightContainer = Color(0xFF410E0B)
private val ErrorDark = Color(0xFFF2B8B5)
private val OnErrorDark = Color(0xFF601410)
private val ErrorDarkContainer = Color(0xFF8C1D18)

private fun lightScheme(
    background: Color,
    surface: Color,
    ink: Color,
    muted: Color,
    line: Color,
    primary: Color,
    container: Color,
    accent: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = container,
    onPrimaryContainer = ink,
    inversePrimary = accent,
    secondary = accent,
    onSecondary = ink,
    secondaryContainer = container,
    onSecondaryContainer = ink,
    tertiary = accent,
    onTertiary = ink,
    tertiaryContainer = container,
    onTertiaryContainer = ink,
    background = background,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = container,
    onSurfaceVariant = muted,
    surfaceTint = primary,
    inverseSurface = ink,
    inverseOnSurface = surface,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorLightContainer,
    onErrorContainer = OnErrorLightContainer,
    outline = muted,
    outlineVariant = line,
    scrim = Color.Black,
    surfaceBright = surface,
    surfaceDim = lerp(background, ink, 0.06f),
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = lerp(background, ink, 0.03f),
    surfaceContainerHigh = lerp(background, ink, 0.05f),
    surfaceContainerHighest = lerp(background, ink, 0.07f),
)

private fun darkScheme(
    background: Color,
    surface: Color,
    ink: Color,
    muted: Color,
    line: Color,
    primary: Color,
    onPrimary: Color,
    container: Color,
    accent: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = container,
    onPrimaryContainer = ink,
    inversePrimary = primary,
    secondary = accent,
    onSecondary = background,
    secondaryContainer = container,
    onSecondaryContainer = ink,
    tertiary = accent,
    onTertiary = background,
    tertiaryContainer = container,
    onTertiaryContainer = ink,
    background = background,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = container,
    onSurfaceVariant = muted,
    surfaceTint = primary,
    inverseSurface = ink,
    inverseOnSurface = background,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorDarkContainer,
    onErrorContainer = ErrorLightContainer,
    outline = muted,
    outlineVariant = line,
    scrim = Color.Black,
    surfaceBright = lerp(surface, ink, 0.10f),
    surfaceDim = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = lerp(surface, background, 0.45f),
    surfaceContainer = surface,
    surfaceContainerHigh = lerp(surface, ink, 0.04f),
    surfaceContainerHighest = lerp(surface, ink, 0.07f),
)

private val Mint = PaletteSpec(
    palette = CuePalette.MINT,
    style = CueStyle.CHEERFUL,
    light = lightScheme(
        background = Color(0xFFF1FBF6),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF0E2A20),
        muted = Color(0xFF567568),
        line = Color(0xFFD8F0E4),
        primary = Color(0xFF0A7F5C),
        container = Color(0xFFE2F8EE),
        accent = Color(0xFF22D3EE),
    ),
    dark = darkScheme(
        background = Color(0xFF0B1512),
        surface = Color(0xFF12201B),
        ink = Color(0xFFE6F5EE),
        muted = Color(0xFF8AA79B),
        line = Color(0xFF1E312A),
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF04231A),
        container = Color(0xFF12302A),
        accent = Color(0xFF38BDF8),
    ),
    heroGradient = listOf(Color(0xFF5EEAD4), Color(0xFF22C55E), Color(0xFF0E7490)),
    glow = Color(0xFF0FAE7B),
)

private val Lagoon = PaletteSpec(
    palette = CuePalette.LAGOON,
    style = CueStyle.CHEERFUL,
    light = lightScheme(
        background = Color(0xFFEFF9FC),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF0C2530),
        muted = Color(0xFF547683),
        line = Color(0xFFD5EBF2),
        primary = Color(0xFF067A8B),
        container = Color(0xFFDFF4FA),
        accent = Color(0xFF6366F1),
    ),
    dark = darkScheme(
        background = Color(0xFF081318),
        surface = Color(0xFF0F1F26),
        ink = Color(0xFFE3F3F9),
        muted = Color(0xFF86A3AE),
        line = Color(0xFF1A2E36),
        primary = Color(0xFF22D3EE),
        onPrimary = Color(0xFF04222B),
        container = Color(0xFF0E2B33),
        accent = Color(0xFF818CF8),
    ),
    heroGradient = listOf(Color(0xFF7DD3FC), Color(0xFF22D3EE), Color(0xFF4338CA)),
    glow = Color(0xFF06A8C4),
)

private val Navy = PaletteSpec(
    palette = CuePalette.NAVY,
    style = CueStyle.PROFESSIONAL,
    light = lightScheme(
        background = Color(0xFFF5F7FB),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF0D1A2B),
        muted = Color(0xFF5A6A80),
        line = Color(0xFFE3E9F1),
        primary = Color(0xFF1E3A8A),
        container = Color(0xFFE8EEFB),
        accent = Color(0xFF2563EB),
    ),
    dark = darkScheme(
        background = Color(0xFF0A101C),
        surface = Color(0xFF111A29),
        ink = Color(0xFFEAF0FA),
        muted = Color(0xFF8B9AB2),
        line = Color(0xFF1E2A3C),
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF061426),
        container = Color(0xFF16233A),
        accent = Color(0xFF93C5FD),
    ),
    heroGradient = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF0B1220)),
    glow = Color(0xFF1E3A8A),
)

private val DeepTeal = PaletteSpec(
    palette = CuePalette.DEEP_TEAL,
    style = CueStyle.PROFESSIONAL,
    light = lightScheme(
        background = Color(0xFFF4F9F8),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF0A2024),
        muted = Color(0xFF557078),
        line = Color(0xFFDCEAEA),
        primary = Color(0xFF0F766E),
        container = Color(0xFFE4F2F1),
        accent = Color(0xFF0E7490),
    ),
    dark = darkScheme(
        background = Color(0xFF07110F),
        surface = Color(0xFF0E1B19),
        ink = Color(0xFFE6F3F1),
        muted = Color(0xFF86A29D),
        line = Color(0xFF1A2B28),
        primary = Color(0xFF2DD4BF),
        onPrimary = Color(0xFF03211D),
        container = Color(0xFF102B27),
        accent = Color(0xFF38BDF8),
    ),
    heroGradient = listOf(Color(0xFF0F766E), Color(0xFF0E7490), Color(0xFF082F49)),
    glow = Color(0xFF0F766E),
)

/** Resolves the persisted palette key into its full specification. */
val CuePalette.spec: PaletteSpec
    get() = when (this) {
        CuePalette.MINT -> Mint
        CuePalette.LAGOON -> Lagoon
        CuePalette.NAVY -> Navy
        CuePalette.DEEP_TEAL -> DeepTeal
    }
