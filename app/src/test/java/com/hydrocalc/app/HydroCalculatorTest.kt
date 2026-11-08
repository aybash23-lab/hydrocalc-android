package com.hydrocalc.app

import com.hydrocalc.app.calc.FlowUnit
import com.hydrocalc.app.calc.HydroCalculator
import com.hydrocalc.app.calc.HydroCalculator.CalcInput
import com.hydrocalc.app.calc.Method
import com.hydrocalc.app.calc.Roughness
import com.hydrocalc.app.calc.TubeMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты расчётного ядра.
 * Эталонные значения получены запуском оригинального JavaScript
 * онлайн-калькулятора prostobuild.by в Node.js.
 */
class HydroCalculatorTest {

    private fun assertClose(expected: Double, actual: Double, relTol: Double = 1e-9) {
        val diff = Math.abs(expected - actual) / Math.max(Math.abs(expected), 1e-12)
        assertTrue("ожидалось $expected, получено $actual", diff < relTol)
    }

    /** Базовый случай сайта: q=15 л/с, D=150, s=5, L=100 м, t=15 °C, сталь новая */
    private val base = CalcInput()

    @Test
    fun sheveler_matchesSiteReference() {
        val r = HydroCalculator.compute(base) // метод по умолчанию — Шевелев
        assertClose(1.0001585, r.ro)
        assertClose(0.9742635981496647, r.velocity)
        assertClose(13.87309758696044, r.i1000 ?: 0.0)
        assertClose(13609.508732808192, r.dpPa)
        assertClose(0.13877836528885987, r.dpKgfCm2)
        assertClose(4.667184064749037, r.sChar)
        assertEquals(1.388, r.dpMeters, 1e-9)
    }

    @Test
    fun theoretical_matchesSiteReference() {
        val r = HydroCalculator.compute(
            base.copy(
                method = Method.TEOR,
                roughness = Roughness.STEEL_CAST_OLD_RUSTY, // 1 мм
                sumKsiText = "1.89"
            )
        )
        assertClose(1.1445289266826343e-6, r.nu ?: 0.0)
        assertClose(14.997622876774033, r.qLpsAtTemp ?: 0.0)
        assertClose(119172.96326995715, r.reynolds ?: 0.0)
        assertClose(0.031978690533252065, r.lambda ?: 0.0)
        assertClose(0.11052370117195574, r.dpFriction ?: 0.0)
        assertClose(0.009145018398952392, r.dpLocal ?: 0.0)
        assertClose(11739.5013899, r.dpPa)
        assertClose(4.025892109021292, r.sChar)
    }

    @Test
    fun snip_matchesSiteReference() {
        val r = HydroCalculator.compute(base.copy(method = Method.SNIP))
        assertClose(9.667636968221986, r.i1000 ?: 0.0)
        assertClose(9483.95186582577, r.dpPa)
        // коэффициенты новой стали
        assertClose(0.226, r.snipM ?: 0.0)
        assertClose(15.9, r.snipA1000 ?: 0.0)
        assertClose(0.684, r.snipC ?: 0.0)
    }

    /** Неновые стальные: таблица СНиП зависит от скорости (v ≤ 1,2 / v > 1,2) */
    @Test
    fun snip_oldSteel_branchByVelocity() {
        val hi = HydroCalculator.compute(
            base.copy(method = Method.SNIP, material = TubeMaterial.STEEL_CAST_OLD, qText = "40")
        )
        assertTrue(hi.velocity > 1.2)
        assertClose(21.0, hi.snipA1000 ?: 0.0)
        assertClose(0.0, hi.snipC ?: 0.0)

        val lo = HydroCalculator.compute(
            base.copy(method = Method.SNIP, material = TubeMaterial.STEEL_CAST_OLD, qText = "10")
        )
        assertTrue(lo.velocity <= 1.2)
        assertClose(17.9, lo.snipA1000 ?: 0.0)
        assertClose(0.867, lo.snipC ?: 0.0)
    }

    /** Ламинарный режим: λ = 75/Re (Пуазейль) */
    @Test
    fun theoretical_laminar_usesPoiseuille() {
        val r = HydroCalculator.compute(
            base.copy(
                method = Method.TEOR, qText = "0.05",
                outerDiameterText = "100", wallThicknessText = "2",
                lengthText = "10", temperatureText = "20"
            )
        )
        assertTrue(r.reynolds ?: 0.0 <= 2320.0)
        assertClose(75.0 / (r.reynolds ?: 1.0), r.lambda ?: 0.0)
    }

    /** Стеклянная гладкая труба (Kэ = 0): ветвь 0,11·(68/Re)^0,25 */
    @Test
    fun theoretical_glass_smoothPipe() {
        val r = HydroCalculator.compute(
            base.copy(method = Method.TEOR, roughness = Roughness.GLASS_SMOOTH)
        )
        assertClose(0.11 * Math.pow(68.0 / (r.reynolds ?: 1.0), 0.25), r.lambda ?: 0.0)
    }

    @Test
    fun optimalDiameter_forBaseInput() {
        val opt = HydroCalculator.optimalDiameter(base)
        assertClose(112.82897536754002, opt.minMm)
        assertClose(276.3734178515251, opt.maxMm)
        assertTrue(opt.fit.contains(125))
        assertTrue(opt.fit.contains(250))
        assertTrue(!opt.fit.contains(100))
    }

    @Test
    fun chartSeries_boundsMatchSiteVelocityRange() {
        val pts = HydroCalculator.chartSeries(base)
        assertTrue(pts.size >= 100)
        // слева v = 0,1 м/с (большой диаметр), справа v = 10 м/с
        assertClose(0.1, pts.first().velocity)
        assertClose(10.0, pts.last().velocity)
        assertClose(436.9847425681294, pts.first().diameterMm, 1e-6)
        assertClose(43.69847425681294, pts.last().diameterMm, 1e-6)
    }

    @Test
    fun validation_rejectsBadInput() {
        assertTrue(HydroCalculator.validate(base.copy(qText = "0")) != null)
        assertTrue(HydroCalculator.validate(base.copy(outerDiameterText = "5", wallThicknessText = "3")) != null)
        assertTrue(HydroCalculator.validate(base.copy(temperatureText = "150")) != null)
        assertTrue(HydroCalculator.validate(base) == null)
    }

    /** Расход в м³/час даёт тот же результат, что и в л/с */
    @Test
    fun flowUnitConversion_isConsistent() {
        val lps = HydroCalculator.compute(base.copy(method = Method.TEOR))
        val mph = HydroCalculator.compute(
            base.copy(method = Method.TEOR, qText = "54", unit = FlowUnit.CUBIC_PER_HOUR)
        )
        assertClose(lps.dpPa, mph.dpPa)
    }
}
