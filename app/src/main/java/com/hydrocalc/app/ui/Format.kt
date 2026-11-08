package com.hydrocalc.app.ui

import java.util.Locale
import kotlin.math.abs

private val RU = Locale("ru", "RU")

/** Три знака после запятой (как в исходном калькуляторе) */
fun fmt3(x: Double): String = String.format(RU, "%.3f", x)

/** Значение с тремя знаками и разделением тысяч; малые значения — с шестью знаками */
fun fmtVal(x: Double): String =
    if (x != 0.0 && abs(x) < 0.001) String.format(RU, "%.6f", x)
    else String.format(RU, "%,.3f", x)

/** Целые с разделением тысяч (число Рейнольдса) */
fun fmtInt(x: Double): String = String.format(RU, "%,.0f", x)

/** Давление в удобных единицах: ≥1000 Па → кПа */
fun fmtPa(x: Double): String =
    if (abs(x) >= 1000.0) String.format(RU, "%.1f кПа", x / 1000.0)
    else String.format(RU, "%.0f Па", x)

private val SUPER = mapOf(
    '-' to "⁻", '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
    '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹"
)

/** Научная запись с русским форматом: 1,145·10⁻⁶ */
fun fmtSci(x: Double): String {
    if (x == 0.0) return "0"
    val exp = kotlin.math.floor(kotlin.math.log10(abs(x))).toInt()
    val mantissa = x / Math.pow(10.0, exp.toDouble())
    val sup = buildString {
        append(if (exp < 0) "-" else "")
        for (ch in abs(exp).toString()) append(SUPER[ch])
    }
    return String.format(RU, "%.3f", mantissa) + "·10" + sup
}
