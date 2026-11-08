package com.hydrocalc.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrocalc.app.calc.HydroCalculator
import com.hydrocalc.app.calc.HydroCalculator.ChartPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * График зависимости потерь давления от внутреннего диаметра.
 * Слева — больший диаметр (v = 0,1 м/с), справа — меньший (v = 10 м/с), как на сайте.
 * Вертикальная шкала логарифмическая; зелёная полоса — зона оптимальных
 * скоростей 0,25–1,5 м/с; красные сегменты кривой — переходная зона Рейнольдса.
 */
@Composable
fun LossChart(
    points: List<ChartPoint>,
    currentDiameterMm: Double?,
    currentDpPa: Double?,
    currentVelocity: Double?,
    showReynoldsColor: Boolean
) {
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 11.sp.toPx() }
            typeface = android.graphics.Typeface.DEFAULT
        }
    }
    val titlePaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 12.sp.toPx() }
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        if (points.size < 2) return@Canvas
        val padLeft = with(density) { 56.dp.toPx() }
        val padRight = with(density) { 16.dp.toPx() }
        val padTop = with(density) { 26.dp.toPx() }
        val padBottom = with(density) { 34.dp.toPx() }
        val left = padLeft
        val right = size.width - padRight
        val top = padTop
        val bottom = size.height - padBottom

        val n = points.size - 1
        val lns = points.map { ln(it.dpPa) }
        var lnMin = lns.min()
        var lnMax = lns.max()
        if (lnMax - lnMin < 1e-6) { lnMin -= 1.0; lnMax += 1.0 }
        val span = lnMax - lnMin

        fun xAt(i: Int) = left + (right - left) * i / n
        fun yAt(dpPa: Double): Float {
            val frac = ((ln(dpPa) - lnMin) / span).toFloat()
            return bottom - (bottom - top) * frac
        }

        // Подписи оси X
        val axisColor = Color(0xFF9AA3AD).toArgb()
        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        labelPaint.color = axisColor
        nativeCanvas.drawText(
            "внутренний диаметр, мм",
            (left + right) / 2f, size.height - 6f, labelPaint
        )
        titlePaint.textAlign = android.graphics.Paint.Align.LEFT
        titlePaint.color = Color(0xFF1A6BB5).toArgb()
        nativeCanvas.drawText("ΔPпот", 4f, 16f, titlePaint)

        // Горизонтальная сетка и подписи Y по «красивым» значениям давления
        val gridColor = Color(0xFFE4E8EC)
        val tickTextColor = Color(0xFF5A6068).toArgb()
        var exp = floor(lnMin / ln(10.0)).toInt()
        val expMax = ceil(lnMax / ln(10.0)).toInt()
        while (exp <= expMax) {
            for (m in intArrayOf(1, 2, 5)) {
                val dpVal = m * Math.pow(10.0, exp.toDouble())
                val lv = ln(dpVal)
                if (lv < lnMin || lv > lnMax) continue
                val y = yAt(dpVal)
                drawLine(gridColor, Offset(left, y), Offset(right, y), 1f)
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                labelPaint.color = tickTextColor
                nativeCanvas.drawText(fmtPa(dpVal), left - 6f, y + 4f, labelPaint)
            }
            exp++
        }

        // Зелёная зона оптимальных скоростей 0,25–1,5 м/с
        val iStart = points.indexOfFirst { it.velocity >= HydroCalculator.V_OPT_MIN }
        val iEnd = points.indexOfLast { it.velocity <= HydroCalculator.V_OPT_MAX }
        if (iStart in 0..iEnd) {
            val x1 = xAt(iStart)
            val x2 = xAt(iEnd)
            drawRect(
                color = ZoneGreenBackground,
                topLeft = Offset(x1, top),
                size = Size(x2 - x1, bottom - top)
            )
        }

        // Оси
        val axisPaintColor = Color(0xFFB4BAC1)
        drawLine(axisPaintColor, Offset(left, top), Offset(left, bottom), 1.5f)
        drawLine(axisPaintColor, Offset(left, bottom), Offset(right, bottom), 1.5f)

        // Кривая потерь давления
        val strokeWidth = with(density) { 2.2.dp.toPx() }
        for (i in 0 until n) {
            val p = points[i]
            val color = if (showReynoldsColor && p.reynolds != null &&
                p.reynolds > 2320.0 && p.reynolds < 4000.0
            ) ZoneRed else CurveColor
            drawLine(
                color = color,
                start = Offset(xAt(i), yAt(p.dpPa)),
                end = Offset(xAt(i + 1), yAt(points[i + 1].dpPa)),
                strokeWidth = strokeWidth
            )
        }

        // Подписи диаметров с цветом по зоне скорости
        val labelCount = 8
        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        for (k in 0..labelCount) {
            val i = k * n / labelCount
            val p = points[i]
            labelPaint.color = velocityZoneColor(p.velocity).toArgb()
            nativeCanvas.drawText(
                p.diameterMm.toInt().toString(), xAt(i), bottom + 14f, labelPaint
            )
        }

        // Маркер текущего диаметра
        if (currentDiameterMm != null && currentDpPa != null) {
            val dLeft = points.first().diameterMm
            val dRight = points.last().diameterMm
            val rel = (dLeft - currentDiameterMm) / (dLeft - dRight)
            if (rel in 0.0..1.0) {
                val cx = left + (right - left) * rel.toFloat()
                val cy = yAt(currentDpPa)
                drawLine(
                    color = Color(0xFF114499),
                    start = Offset(cx, top),
                    end = Offset(cx, bottom),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                )
                drawCircle(Color(0xFF114499), radius = 6f, center = Offset(cx, cy))
                drawCircle(Color.White, radius = 2.6f, center = Offset(cx, cy))
                val text = fmtPa(currentDpPa) +
                        (currentVelocity?.let { " · v = " + fmt3(it) + " м/с" } ?: "")
                labelPaint.textAlign = if (rel > 0.8) android.graphics.Paint.Align.RIGHT
                else android.graphics.Paint.Align.LEFT
                labelPaint.color = Color(0xFF114499).toArgb()
                val ty = if (cy - 12f < top + 14f) cy + 22f else cy - 12f
                nativeCanvas.drawText(text, cx + 9f, ty, labelPaint)
            } else {
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                labelPaint.color = ZoneRed.toArgb()
                nativeCanvas.drawText(
                    "текущая точка вне диапазона графика",
                    (left + right) / 2f, top + 12f, labelPaint
                )
            }
        }
    }
}

/** Легенда под графиком */
@Composable
fun ChartLegend(showReynoldsColor: Boolean) {
    Column {
        LegendItem(ZoneGreenBackground, ZoneGreen, "v = 0,25–1,5 м/с — оптимальная зона")
        LegendItem(Color.Transparent, ZoneAmber, "жёлтые подписи диаметров — v вне оптимальной зоны")
        LegendItem(Color.Transparent, ZoneRed, "красные подписи — v вне допустимых значений")
        if (showReynoldsColor) {
            LegendItem(Color.Transparent, ZoneRed, "красная кривая — переходная зона Re = 2300–4000")
        }
    }
}

@Composable
private fun LegendItem(bg: Color, fg: Color, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier
                .size(10.dp)
                .background(bg, shape = androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, fg, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
