package com.johnreyes.fitbod.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line chart with a soft fill, used for estimated 1RM over time. */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 160.dp, color: Color = Palette.Accent) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 8.dp.toPx()
        val h = size.height - pad * 2
        fun x(i: Int) = if (values.size == 1) size.width / 2 else pad + i * (size.width - pad * 2) / (values.size - 1)
        fun y(v: Double) = pad + h - ((v - min) / span * h).toFloat()

        for (g in 0..3) {
            val gy = pad + h * g / 3f
            drawLine(Palette.Line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.forEachIndexed { i, v ->
            drawCircle(Palette.Bg, 5.dp.toPx(), Offset(x(i), y(v)))
            drawCircle(color, 3.5.dp.toPx(), Offset(x(i), y(v)))
        }
    }
}

/** Vertical bars with labels underneath, used for weekly volume. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val max = values.max().takeIf { it > 0 } ?: 1.0
            val slot = size.width / values.size
            val bar = slot * 0.56f
            values.forEachIndexed { i, v ->
                val bh = (v / max * (size.height - 4.dp.toPx())).toFloat().coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = if (i == values.lastIndex) Palette.Accent else Palette.CardHi,
                    topLeft = Offset(i * slot + (slot - bar) / 2, size.height - bh),
                    size = Size(bar, bh),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Tiny trend line for list rows. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = i * size.width / (values.size - 1)
            val py = size.height - ((v - min) / span * size.height).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val up = values.last() >= values.first()
        drawPath(path, if (up) Palette.Good else Palette.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
