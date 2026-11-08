package io.github.rpn17m2g.cue.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The Cue mark: a "C" that opens into a play triangle. It replaces the generic
 * sparkle next to the wordmark and is the same shape the launcher icon uses, so
 * the app has one recognisable mark instead of two unrelated ones.
 *
 * Drawn rather than loaded so it takes the palette's colours directly and stays
 * crisp at every size.
 */
@Composable
fun CueMark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(color = tint, radius = radius)
        val ringRadius = radius * 0.62f
        val stroke = radius * 0.30f
        // Start just above the 3 o'clock mark and sweep clockwise most of the
        // way round, leaving the opening on the right for the play triangle.
        drawArc(
            color = contentColor,
            startAngle = 52f,
            sweepAngle = 256f,
            useCenter = false,
            topLeft = Offset(
                center.x - ringRadius,
                center.y - ringRadius,
            ),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val triangle = Path().apply {
            moveTo(center.x + radius * 0.02f, center.y - radius * 0.30f)
            lineTo(center.x + radius * 0.46f, center.y)
            lineTo(center.x + radius * 0.02f, center.y + radius * 0.30f)
            close()
        }
        drawPath(path = triangle, color = contentColor)
    }
}
