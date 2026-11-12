package com.hydrocalc.app.calc

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Расчётное ядро гидравлического расчёта трубопровода.
 *
 * Формулы воспроизводят онлайн-калькулятор prostobuild.by
 * (https://prostobuild.by/onlainraschet/244-gidravlicheskiy-raschet-truboprovoda-onlayn.html):
 *
 *  1) Таблицы Шевелева:        1000i = tk·(q^tb / d^tm);  ΔP = 1000i·9.81·L, Па
 *  2) Теоретическая гидравлика: Re, λ (Пуазейль / Блазиус / Альтшуль),
 *     R = λ·v²·ρ/(2·9.81·d·10), кгс/(см²·м); местные: ΔPмс = Σζ·v²·ρ·1000/(2·9.81·10000)
 *  3) СНиП 2.04.02-84:          1000i = (A1000/19.61)·((A0 + C/v)^m / d^(m+1))·v²
 *
 * Общее: ρ(t) = (−0.003·t² − 0.1511·t + 1003.1)/1000, т/м³
 *        v = 4q/(ρ·π·d²), м/с
 *        S = ΔP/(q·3600)², Па/(т/ч)²
 *
 * Надбавка на местные сопротивления (+5 %): методы Шевелева и СНиП
 * дают только потери на трение по длине, местные сопротивления
 * добавляются множителем 1.05 к полным потерям давления.
 * В методе «Теория» местные потери уже учтены явно через Σζ,
 * поэтому надбавка там не применяется.
 */
object HydroCalculator {

    /** Границы оптимальной скорости воды, м/с (зелёная зона исходного калькулятора) */
    const val V_OPT_MIN = 0.25
    const val V_OPT_MAX = 1.5

    /** Диапазон скоростей, отображаемых на графике, м/с */
    const val V_CHART_MIN = 0.1
    const val V_CHART_MAX = 10.0

    /** Множитель надбавки на местные сопротивления (галочка +5 %) */
    const val LOCAL_LOSSES_FACTOR = 1.05

    // ------------------------------------------------------------------
    // Входные данные
    // ------------------------------------------------------------------

    data class CalcInput(
        val method: Method = Method.SHEVELER,
        val material: TubeMaterial = TubeMaterial.STEEL_NEW,
        val qText: String = "15",
        val unit: FlowUnit = FlowUnit.LPS,
        val outerDiameterText: String = "150",
        val wallThicknessText: String = "5",
        val lengthText: String = "100",
        val temperatureText: String = "15",
        val roughness: Roughness = Roughness.STEEL_CAST_OLD_RUSTY,
        val sumKsiText: String = "1.89",
        val localLosses: Boolean = false
    )

    // ------------------------------------------------------------------
    // Результат
    // ------------------------------------------------------------------

    data class CalcResult(
        val method: Method,
        val qM3s: Double,          // расход, м³/с
        val innerDiameterM: Double, // внутренний диаметр, м
        val lengthM: Double,        // длина, м
        val ro: Double,             // плотность воды, т/м³
        val velocity: Double,       // скорость, м/с
        // теоретическая гидравлика
        val nu: Double?,            // кинематическая вязкость, м²/с
        val qLpsAtTemp: Double?,    // расход при рабочей температуре, л/с
        val reynolds: Double?,
        val lambda: Double?,        // коэффициент гидравлического трения
        val rSpecific: Double?,     // удельные потери на трение, кгс/(см²·м)
        val dpFriction: Double?,    // потери на трение, кгс/см²
        val dpLocal: Double?,       // потери в местных сопротивлениях, кгс/см²
        // СНиП
        val snipM: Double?,
        val snipA0: Double?,
        val snipA1000: Double?,
        val snipC: Double?,
        // общие
        val i1000: Double?,         // 1000i, м вод. ст. на 1 км (Шевелев / СНиП)
        val sChar: Double,          // характеристика сопротивления, Па/(т/ч)²
        val dpKgfCm2: Double,       // потери давления, кгс/см²
        val dpPa: Double,           // потери давления, Па
        val dpMeters: Double,       // потери давления, м вод. ст.
        val localLosses: Boolean,   // надбавка +5 % на местные сопротивления учтена
        val warnings: List<String>
    )

    // ------------------------------------------------------------------
    // Физика воды
    // ------------------------------------------------------------------

    /** Плотность воды при температуре t, °C — т/м³ (аппроксимация исходного калькулятора) */
    fun waterDensity(t: Double): Double = (-0.003 * t * t - 0.1511 * t + 1003.1) / 1000.0

    /** Кинематический коэффициент вязкости воды, м²/с (формула Пуазейля) */
    fun waterViscosity(t: Double): Double = 0.0178 / (1.0 + 0.0337 * t + 0.000221 * t * t) / 1e4

    /** Внутренний диаметр, соответствующий скорости v, м (при расходе q, м³/с) */
    fun diameterForVelocity(qM3s: Double, ro: Double, v: Double): Double =
        sqrt(4.0 * qM3s / ro / PI / v)

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** Возвращает текст ошибки, если исходные данные некорректны, иначе null */
    fun validate(inp: CalcInput): String? {
        val q = parse(inp.qText) ?: return "Укажите расчётный расход"
        if (q <= 0.0) return "Расчётный расход должен быть больше нуля"
        val d = parse(inp.outerDiameterText) ?: return "Укажите наружный диаметр"
        if (d <= 0.0) return "Наружный диаметр должен быть больше нуля"
        val s = parse(inp.wallThicknessText) ?: return "Укажите толщину стенки"
        if (s < 0.0) return "Толщина стенки не может быть отрицательной"
        if (d - 2.0 * s <= 0.0) return "Внутренний диаметр (D − 2·s) должен быть больше нуля"
        val l = parse(inp.lengthText) ?: return "Укажите длину трубопровода"
        if (l <= 0.0) return "Длина трубопровода должна быть больше нуля"
        val t = parse(inp.temperatureText) ?: return "Укажите среднюю температуру воды"
        if (t < 0.0 || t > 100.0) return "Средняя температура воды: от 0 до 100 °C"
        if (inp.method == Method.TEOR) {
            val ksi = parse(inp.sumKsiText) ?: return "Укажите сумму коэффициентов местных сопротивлений"
            if (ksi < 0.0) return "Сумма коэффициентов местных сопротивлений не может быть отрицательной"
        }
        return null
    }

    // ------------------------------------------------------------------
    // Основной расчёт
    // ------------------------------------------------------------------

    fun compute(inp: CalcInput): CalcResult {
        val q = (parse(inp.qText) ?: 0.0) / inp.unit.divisor
        val dInner = ((parse(inp.outerDiameterText) ?: 0.0) -
                2.0 * (parse(inp.wallThicknessText) ?: 0.0)) / 1000.0
        val l = parse(inp.lengthText) ?: 0.0
        val tMid = parse(inp.temperatureText) ?: 0.0
        val sumKsi = if (inp.method == Method.TEOR) parse(inp.sumKsiText) ?: 0.0 else 0.0
        return computeParsed(
            inp.method, inp.material, q, dInner, l, tMid, inp.roughness.mm, sumKsi,
            inp.localLosses
        )
    }

    /**
     * Полный расчёт по выбранному методу.
     * Формулы и порядок операций соответствуют функции calc() исходного калькулятора.
     *
     * @param innerDiameterM внутренний диаметр, м (для графика можно передать произвольный)
     * @param localLosses учитывать надбавку +5 % на местные сопротивления
     * (только для методов Шевелева и СНиП)
     */
    fun computeParsed(
        method: Method,
        material: TubeMaterial,
        qM3s: Double,
        innerDiameterM: Double,
        lengthM: Double,
        tMid: Double,
        roughnessMm: Double,
        sumKsi: Double,
        localLosses: Boolean = false
    ): CalcResult {
        val ro = waterDensity(tMid)
        val v = 4.0 * qM3s / ro / PI / innerDiameterM.pow(2)

        var nu: Double? = null
        var qLps: Double? = null
        var re: Double? = null
        var lambda: Double? = null
        var rSpec: Double? = null
        var dpFr: Double? = null
        var dpMs: Double? = null
        var snipM: Double? = null
        var snipA0: Double? = null
        var snipA1000: Double? = null
        var snipC: Double? = null
        var i1000: Double? = null

        var dpPa: Double
        var dpKgf: Double

        when (method) {
            Method.SHEVELER -> {
                // 1000i, м вод. ст. на 1 км
                val i = material.tk * (qM3s.pow(material.tb) / innerDiameterM.pow(material.tm))
                i1000 = i
                dpPa = i * 9.81 * lengthM
                dpKgf = dpPa * 1.0197162e-5
            }

            Method.TEOR -> {
                // кинематический коэффициент вязкости, м²/с
                val n = waterViscosity(tMid)
                val g = qM3s / ro * 1000.0 // расход при рабочей температуре, л/с
                val r = v * innerDiameterM / n
                val sher = roughnessMm / 1000.0
                // λ: ламинарный (Пуазейль) → Блазиус → Альтшуль (переходный → квадратичный)
                val lam = when {
                    r <= 2320.0 -> 75.0 / r
                    r <= 4000.0 -> 0.3164 / r.pow(0.25)
                    r <= 500.0 * innerDiameterM / sher ->
                        0.11 * (68.0 / r + sher / innerDiameterM).pow(0.25)
                    else -> 0.11 * (sher / innerDiameterM).pow(0.25)
                }
                // удельные потери давления на трение, кгс/(см²·м)
                val rUd = lam * v.pow(2) * ro / 2.0 / 9.81 / innerDiameterM / 10.0
                val dpFriction = rUd * lengthM
                val dpLocal = sumKsi * v.pow(2) * ro * 1000.0 / 2.0 / 9.81 / 10000.0
                val dp = dpFriction + dpLocal
                nu = n; qLps = g; re = r; lambda = lam
                rSpec = rUd; dpFr = dpFriction; dpMs = dpLocal
                dpKgf = dp
                dpPa = dp * 9.81 * 10000.0
            }

            Method.SNIP -> {
                val sn = material.snipCoef(v)
                val i = sn.a1000 / 19.61 *
                        (sn.a0 + sn.c / v).pow(sn.m) /
                        innerDiameterM.pow(sn.m + 1.0) * v.pow(2)
                snipM = sn.m; snipA0 = sn.a0; snipA1000 = sn.a1000; snipC = sn.c
                i1000 = i
                dpPa = i * 9.81 * lengthM
                dpKgf = dpPa * 1.0197162e-5
            }
        }

        // Надбавка на местные сопротивления (+5 %): применяется только к методам,
        // которые считают трение по длине без местных потерь (Шевелев, СНиП).
        val kLocal = if (method != Method.TEOR && localLosses) LOCAL_LOSSES_FACTOR else 1.0
        dpPa *= kLocal
        dpKgf *= kLocal

        val s = dpPa / (qM3s * 3600.0).pow(2)
        val dpMeters = round(dpPa / 9807.0 * 1000.0) / 1000.0

        val warnings = mutableListOf<String>()
        if (v < V_OPT_MIN) warnings.add(
            "Скорость ниже 0,25 м/с — возможно отложение осадка и завышенный диаметр"
        )
        if (v > V_OPT_MAX) warnings.add(
            "Скорость выше 1,5 м/с — возможны шум, гидроудары и повышенные потери"
        )
        if (v > V_CHART_MAX) warnings.add("Скорость превышает 10 м/с — увеличьте диаметр")
        if (method == Method.TEOR && re != null && re > 2320.0 && re < 4000.0) {
            warnings.add("Число Рейнольдса в переходной зоне (2300–4000) — расчёт менее точен")
        }

        return CalcResult(
            method = method,
            qM3s = qM3s,
            innerDiameterM = innerDiameterM,
            lengthM = lengthM,
            ro = ro,
            velocity = v,
            nu = nu,
            qLpsAtTemp = qLps,
            reynolds = re,
            lambda = lambda,
            rSpecific = rSpec,
            dpFriction = dpFr,
            dpLocal = dpMs,
            snipM = snipM,
            snipA0 = snipA0,
            snipA1000 = snipA1000,
            snipC = snipC,
            i1000 = i1000,
            sChar = s,
            dpKgfCm2 = dpKgf,
            dpPa = dpPa,
            dpMeters = dpMeters,
            localLosses = kLocal > 1.0,
            warnings = warnings
        )
    }

    // ------------------------------------------------------------------
    // График: зависимость потерь давления от внутреннего диаметра
    // ------------------------------------------------------------------

    /** Точка кривой ΔP(D) */
    data class ChartPoint(
        val diameterMm: Double,
        val dpPa: Double,
        val velocity: Double,
        val reynolds: Double?
    )

    /**
     * Кривая потерь давления по диапазону диаметров,
     * соответствующему скоростям 0,1…10 м/с (слева — больший диаметр, как на сайте).
     */
    fun chartSeries(inp: CalcInput, steps: Int = 120): List<ChartPoint> {
        val q = (parse(inp.qText) ?: 0.0) / inp.unit.divisor
        val l = parse(inp.lengthText) ?: 0.0
        val tMid = parse(inp.temperatureText) ?: 0.0
        val sumKsi = if (inp.method == Method.TEOR) parse(inp.sumKsiText) ?: 0.0 else 0.0
        val ro = waterDensity(tMid)
        val dLeft = diameterForVelocity(q, ro, V_CHART_MIN)   // больший диаметр (v = 0,1)
        val dRight = diameterForVelocity(q, ro, V_CHART_MAX)  // меньший диаметр (v = 10)
        val list = ArrayList<ChartPoint>(steps + 1)
        for (i in 0..steps) {
            val d = dLeft - (dLeft - dRight) * i / steps
            val r = computeParsed(
                inp.method, inp.material, q, d, l, tMid, inp.roughness.mm, sumKsi,
                inp.localLosses
            )
            list.add(ChartPoint(d * 1000.0, r.dpPa, r.velocity, r.reynolds))
        }
        return list
    }

    // ------------------------------------------------------------------
    // Оптимальный диаметр
    // ------------------------------------------------------------------

    /** Диапазон оптимальных внутренних диаметров, мм (скорость 0,25…1,5 м/с) */
    data class OptimalRange(val minMm: Double, val maxMm: Double, val fit: List<Int>)

    fun optimalDiameter(inp: CalcInput): OptimalRange {
        val q = (parse(inp.qText) ?: 0.0) / inp.unit.divisor
        val ro = waterDensity(parse(inp.temperatureText) ?: 0.0)
        val dMin = diameterForVelocity(q, ro, V_OPT_MAX) * 1000.0 // при v = 1,5
        val dMax = diameterForVelocity(q, ro, V_OPT_MIN) * 1000.0 // при v = 0,25
        val fit = STANDARD_NOMINAL_DIAMETERS_MM.filter { it >= dMin && it <= dMax }
        return OptimalRange(dMin, dMax, fit)
    }
}
