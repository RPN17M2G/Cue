package io.github.rpn17m2g.cue.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.rpn17m2g.cue.settings.CuePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the palette tables against the usual failure mode of a hand-written
 * theme: a pretty colour that cannot be read. Every scheme is checked, because
 * the app ships all eight and the user can switch at any time.
 *
 * The thresholds are the ones in `docs/UI-REDESIGN-PLAN.md` §9: body text at
 * WCAG AA (4.5:1) and primary text at the same level, since primary is used for
 * section labels, not only for large shapes.
 */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun schemes(): List<Pair<String, ColorScheme>> =
        CuePalette.entries.flatMap { palette ->
            val spec = palette.spec
            listOf(
                "${palette.name} light" to spec.light,
                "${palette.name} dark" to spec.dark,
            )
        }

    @Test
    fun `body text meets WCAG AA on every scheme`() {
        schemes().forEach { (name, scheme) ->
            assertAtLeast(name, "onBackground/background", scheme.onBackground, scheme.background, 4.5)
            assertAtLeast(name, "onSurface/surface", scheme.onSurface, scheme.surface, 4.5)
            assertAtLeast(name, "onSurfaceVariant/surface", scheme.onSurfaceVariant, scheme.surface, 4.5)
            assertAtLeast(name, "onSurfaceVariant/background", scheme.onSurfaceVariant, scheme.background, 4.5)
        }
    }

    @Test
    fun `primary and its container stay readable`() {
        schemes().forEach { (name, scheme) ->
            assertAtLeast(name, "primary/background", scheme.primary, scheme.background, 4.5)
            assertAtLeast(name, "onPrimary/primary", scheme.onPrimary, scheme.primary, 4.5)
            assertAtLeast(
                name,
                "onPrimaryContainer/primaryContainer",
                scheme.onPrimaryContainer,
                scheme.primaryContainer,
                4.5,
            )
        }
    }

    @Test
    fun `every palette resolves to a light and a dark scheme`() {
        assertEquals(CuePalette.entries.size, CuePalette.entries.map { it.spec }.size)
        CuePalette.entries.forEach { palette ->
            val spec = palette.spec
            assertEquals(palette, spec.palette)
            assertTrue(
                "${palette.name}: light and dark must differ",
                spec.light.background != spec.dark.background,
            )
            assertEquals("${palette.name}: hero gradient stops", 3, spec.heroGradient.size)
        }
    }

    private fun assertAtLeast(name: String, what: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$name: $what is ${(ratio * 100).toInt() / 100.0}:1, need $minimum:1",
            ratio >= minimum,
        )
    }
}
