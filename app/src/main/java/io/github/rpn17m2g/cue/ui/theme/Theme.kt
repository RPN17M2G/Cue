package io.github.rpn17m2g.cue.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.rpn17m2g.cue.settings.AppTheme
import io.github.rpn17m2g.cue.settings.CuePalette

/**
 * One theme, two axes: [palette] supplies the colour, shape and type
 * personality, [theme] the light/dark mode. Both come from the same settings
 * flow, so a change re-composes the whole tree — no restart.
 */
@Composable
fun CueTheme(
    palette: CuePalette = CuePalette.MINT,
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val spec = palette.spec
    val dark = theme.isDark()
    val colorScheme = if (dark) spec.dark else spec.light
    val tokens = cueTokens(spec)
    // Edge-to-edge picks the system-bar icon colour from the system
    // configuration, but Settings can force the app into the other theme. In
    // light mode over a dark system that leaves white icons on a white bar, so
    // the appearance follows the resolved app theme instead.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalCueTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = cueTypography(spec.style),
            shapes = cueShapes(spec.style),
            content = content,
        )
    }
}

/**
 * Type weight is part of the palette's personality: cheerful themes lean heavy
 * and tight, professional ones stay calm. Only the handful of styles the app
 * actually uses are overridden.
 */
private fun cueTypography(style: CueStyle): Typography {
    val cheerful = style == CueStyle.CHEERFUL
    val heading = if (cheerful) FontWeight.ExtraBold else FontWeight.SemiBold
    val title = if (cheerful) FontWeight.Bold else FontWeight.SemiBold
    val label = if (cheerful) FontWeight.SemiBold else FontWeight.Medium
    val tracking = if (cheerful) (-0.02).em else (-0.01).em
    return Typography(
        displaySmall = TextStyle(
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = heading,
            letterSpacing = tracking,
        ),
        headlineMedium = TextStyle(
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = heading,
            letterSpacing = tracking,
        ),
        titleLarge = TextStyle(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = title,
            letterSpacing = tracking,
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = title,
            letterSpacing = tracking,
        ),
        bodyLarge = TextStyle(
            fontSize = if (cheerful) 16.sp else 15.sp,
            lineHeight = 24.sp,
            fontWeight = if (cheerful) FontWeight.Medium else FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontSize = if (cheerful) 14.sp else 13.sp,
            lineHeight = if (cheerful) 20.sp else 18.sp,
            fontWeight = label,
        ),
    )
}

/** Resolves the theme setting against the system, for both colours and bars. */
@Composable
private fun AppTheme.isDark(): Boolean = when (this) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
