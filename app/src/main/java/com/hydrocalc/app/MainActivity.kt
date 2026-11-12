package com.hydrocalc.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydrocalc.app.calc.FlowUnit
import com.hydrocalc.app.calc.HydroCalculator
import com.hydrocalc.app.calc.HydroCalculator.CalcInput
import com.hydrocalc.app.calc.Method
import com.hydrocalc.app.calc.Roughness
import com.hydrocalc.app.calc.TubeMaterial
import com.hydrocalc.app.ui.ChartLegend
import com.hydrocalc.app.ui.DropdownField
import com.hydrocalc.app.ui.LocalLossesCheckbox
import com.hydrocalc.app.ui.LossChart
import com.hydrocalc.app.ui.NumberField
import com.hydrocalc.app.ui.ResultRow
import com.hydrocalc.app.ui.SectionCard
import com.hydrocalc.app.ui.ZoneRed
import com.hydrocalc.app.ui.fmt3
import com.hydrocalc.app.ui.fmtInt
import com.hydrocalc.app.ui.fmtSci
import com.hydrocalc.app.ui.fmtVal
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.hydrocalc.app.ui.HydroCalcTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorScreen()
                }
            }
        }
    }
}

/** Строка результата для экрана и отчёта */
private data class ResultRowData(
    val label: String,
    val value: String,
    val emphasize: Boolean = false,
    val warn: Boolean = false
)

private fun methodShort(m: Method): String = when (m) {
    Method.SHEVELER -> "Шевелев"
    Method.TEOR -> "Теория"
    Method.SNIP -> "СНиП"
}

/** Все строки результатов расчёта (трассировка, как на сайте) */
private fun resultRows(res: HydroCalculator.CalcResult): List<ResultRowData> {
    val rows = mutableListOf<ResultRowData>()
    rows += ResultRowData("Плотность воды при t ср, т/м³", fmt3(res.ro))
    rows += ResultRowData(
        "Скорость воды, м/с", fmt3(res.velocity),
        warn = res.velocity < HydroCalculator.V_OPT_MIN || res.velocity > HydroCalculator.V_OPT_MAX
    )
    if (res.method == Method.TEOR) {
        rows += ResultRowData("Кинематический к-т вязкости, м²/с", fmtSci(res.nu ?: 0.0))
        rows += ResultRowData("Расход воды при t ср, л/с", fmt3(res.qLpsAtTemp ?: 0.0))
        rows += ResultRowData(
            "Число Рейнольдса", fmtInt(res.reynolds ?: 0.0),
            warn = res.reynolds != null && res.reynolds > 2320.0 && res.reynolds < 4000.0
        )
        rows += ResultRowData("К-т гидравлического трения λ", fmt3(res.lambda ?: 0.0))
        rows += ResultRowData("Удельные потери на трение R, кгс/(см²·м)", fmtVal(res.rSpecific ?: 0.0))
        rows += ResultRowData("Потери давления на трение, кгс/см²", fmtVal(res.dpFriction ?: 0.0))
        rows += ResultRowData("Потери в местных сопротивлениях, кгс/см²", fmtVal(res.dpLocal ?: 0.0))
    }
    if (res.method == Method.SNIP) {
        rows += ResultRowData("Показатель степени m", fmt3(res.snipM ?: 0.0))
        rows += ResultRowData("A₀", fmt3(res.snipA0 ?: 0.0))
        rows += ResultRowData("1000·A₁", fmt3(res.snipA1000 ?: 0.0))
        rows += ResultRowData("C", fmt3(res.snipC ?: 0.0))
    }
    if (res.i1000 != null) {
        rows += ResultRowData("1000i, м вод. ст. на 1 км", fmtVal(res.i1000 ?: 0.0))
    }
        if (res.localLosses) {
        rows += ResultRowData("Надбавка на местные сопротивления", "+5%")
    }
    rows += ResultRowData("Характеристика сопротивления, Па/(т/ч)²", fmtVal(res.sChar), emphasize = true)
    rows += ResultRowData("Потери давления, кгс/см²", fmtVal(res.dpKgfCm2), emphasize = true)
    rows += ResultRowData("Потери давления, Па", fmtVal(res.dpPa), emphasize = true)
    rows += ResultRowData("Потери давления, м вод. ст.", fmt3(res.dpMeters), emphasize = true)
    return rows
}

/** Текстовый отчёт для «Поделиться» и буфера обмена */
private fun buildReport(inp: CalcInput, res: HydroCalculator.CalcResult): String = buildString {
    appendLine("ГИДРАВЛИЧЕСКИЙ РАСЧЁТ ТРУБОПРОВОДА")
    appendLine("Метод: ${inp.method.title}")
    appendLine()
    appendLine("— Исходные данные —")
    appendLine("Материал: ${inp.material.title}")
    appendLine("Расчётный расход: ${inp.qText.trim()} ${inp.unit.title}")
    appendLine("Наружный диаметр: ${inp.outerDiameterText.trim()} мм")
    appendLine("Толщина стенки: ${inp.wallThicknessText.trim()} мм")
    appendLine("Внутренний диаметр: ${fmt3(res.innerDiameterM * 1000.0)} мм")
    appendLine("Длина трубопровода: ${inp.lengthText.trim()} м")
    appendLine("Средняя температура воды: ${inp.temperatureText.trim()} °C")
    if (inp.method == Method.TEOR) {
        appendLine("Экв. шероховатость: ${inp.roughness.mm} мм")
        appendLine("Сумма к-тов местных сопротивлений: ${inp.sumKsiText.trim()}")
    }
    appendLine()
    appendLine("— Результаты —")
    for (r in resultRows(res)) appendLine("${r.label}: ${r.value}")
    if (res.warnings.isNotEmpty()) {
        appendLine()
        appendLine("Примечания:")
        for (w in res.warnings) appendLine("• $w")
    }
    appendLine()
    appendLine("Расчёт по методике онлайн-калькулятора prostobuild.by")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    var method by remember { mutableStateOf(Method.SHEVELER) }
    var material by remember { mutableStateOf(TubeMaterial.STEEL_NEW) }
    var qText by remember { mutableStateOf("15") }
    var unit by remember { mutableStateOf(FlowUnit.LPS) }
    var dText by remember { mutableStateOf("150") }
    var sText by remember { mutableStateOf("5") }
    var lText by remember { mutableStateOf("100") }
    var tText by remember { mutableStateOf("15") }
    var roughness by remember { mutableStateOf(Roughness.STEEL_CAST_OLD_RUSTY) }
    var ksiText by remember { mutableStateOf("1.89") }
    var showAbout by remember { mutableStateOf(false) }
    var localLosses by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val inp = CalcInput(
        method = method, material = material,
        qText = qText, unit = unit,
        outerDiameterText = dText, wallThicknessText = sText,
        lengthText = lText, temperatureText = tText,
        roughness = roughness, sumKsiText = ksiText,
        localLosses = localLosses
    )
    val error = HydroCalculator.validate(inp)
    val result = if (error == null) HydroCalculator.compute(inp) else null
    val chartPoints = remember(inp, error) {
        if (error == null) HydroCalculator.chartSeries(inp) else emptyList()
    }
    val optimal = remember(inp, error) {
        if (error == null) HydroCalculator.optimalDiameter(inp) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Гидравлический расчёт", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "О методике")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Метод ----------
            SectionCard("Метод расчёта") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Method.values().forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = method == m,
                            onClick = { method = m },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index, count = Method.values().size
                            )
                        ) { Text(methodShort(m)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (method) {
                        Method.SHEVELER -> "Эмпирические формулы Ф. А. Шевелева для водопроводных труб"
                        Method.TEOR -> "Число Рейнольдса, λ (Пуазейль / Блазиус / Альтшуль), местные сопротивления"
                        Method.SNIP -> "Формула с табличными коэффициентами СНиП 2.04.02-84"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- Исходные данные ----------
            SectionCard("Исходные данные") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropdownField(
                        label = "Материал трубопровода",
                        options = TubeMaterial.values().map { it.title },
                        selectedIndex = material.ordinal,
                        onSelect = { material = TubeMaterial.values()[it] }
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        NumberField(
                            label = "Расчётный расход",
                            value = qText,
                            onValueChange = { qText = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        DropdownField(
                            label = "Ед.",
                            options = FlowUnit.values().map { it.title },
                            selectedIndex = unit.ordinal,
                            onSelect = { unit = FlowUnit.values()[it] },
                            compact = true,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            label = "Наружный диаметр",
                            value = dText,
                            onValueChange = { dText = it },
                            suffix = "мм",
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "Толщина стенки",
                            value = sText,
                            onValueChange = { sText = it },
                            suffix = "мм",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            label = "Длина трубопровода",
                            value = lText,
                            onValueChange = { lText = it },
                            suffix = "м",
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "Средняя темп. воды",
                            value = tText,
                            onValueChange = { tText = it },
                            suffix = "°C",
                            modifier = Modifier.weight(1f)
                        )
                    }
                                        if (method != Method.TEOR) {
                        LocalLossesCheckbox(
                            checked = localLosses,
                            onCheckedChange = { localLosses = it }
                        )
                    }
                    if (method == Method.TEOR) {
                        DropdownField(
                            label = "Экв. шероховатость внутр. поверхностей",
                            options = Roughness.values().map { it.title },
                            selectedIndex = roughness.ordinal,
                            onSelect = { roughness = Roughness.values()[it] }
                        )
                        NumberField(
                            label = "Сумма к-тов местных сопротивлений Σζ",
                            value = ksiText,
                            onValueChange = { ksiText = it }
                        )
                    }
                }
            }

            // ---------- Ошибка ввода ----------
            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // ---------- Результаты ----------
            if (result != null) {
                SectionCard("Результаты расчёта") {
                    Column {
                        resultRows(result).forEach { r ->
                            ResultRow(r.label, r.value, emphasize = r.emphasize, warn = r.warn)
                        }
                        result.warnings.forEach {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⚠ $it",
                                color = ZoneRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // ---------- График ----------
                SectionCard("Потери давления от диаметра трубы") {
                    LossChart(
                        points = chartPoints,
                        currentDiameterMm = result.innerDiameterM * 1000.0,
                        currentDpPa = result.dpPa,
                        currentVelocity = result.velocity,
                        showReynoldsColor = method == Method.TEOR
                    )
                    Spacer(Modifier.height(8.dp))
                    ChartLegend(showReynoldsColor = method == Method.TEOR)
                }

                // ---------- Оптимальный диаметр ----------
                if (optimal != null) {
                    SectionCard("Оптимальный диаметр (v = 0,25–1,5 м/с)") {
                        Text(
                            "Внутренний диаметр: " +
                                    "${fmtInt(optimal.minMm)}–${fmtInt(optimal.maxMm)} мм"
                        )
                        Spacer(Modifier.height(4.dp))
                        if (optimal.fit.isNotEmpty()) {
                            Text(
                                "Подходят условные проходы: " +
                                        optimal.fit.joinToString(", ") { "$it" } + " мм",
                                color = com.hydrocalc.app.ui.ZoneGreen
                            )
                        } else {
                            val nearest = com.hydrocalc.app.calc.STANDARD_NOMINAL_DIAMETERS_MM
                                .minByOrNull { abs(it - (optimal.minMm + optimal.maxMm) / 2.0) }
                            Text(
                                "Стандартный условный проход в зоне отсутствует, " +
                                        "ближайший: $nearest мм",
                                color = com.hydrocalc.app.ui.ZoneAmber
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Справочно: список распространённых условных проходов. " +
                                    "Точный внутренний диаметр зависит от типа и толщины стенки трубы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---------- Экспорт ----------
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Гидрорасчёт", buildReport(inp, result))
                            )
                            android.widget.Toast.makeText(
                                context, "Отчёт скопирован", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Копировать")
                    }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildReport(inp, result))
                                putExtra(Intent.EXTRA_SUBJECT, "Гидравлический расчёт трубопровода")
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Поделиться отчётом")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Поделиться")
                    }
                }
            }

            Text(
                "Формулы соответствуют онлайн-калькулятору prostobuild.by",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) { Text("Закрыть") }
                },
                title = { Text("О методике") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• Таблицы Шевелева — эмпирические формулы Ф. А. Шевелева " +
                                "для стальных, чугунных, асбестоцементных, железобетонных " +
                                "и пластмассовых водопроводных труб.")
                        Text("• Теоретическая гидравлика — число Рейнольдса, коэффициент трения " +
                                "λ (Пуазейль при Re ≤ 2320, Блазиус при Re ≤ 4000, Альтшуль далее), " +
                                "потери на трение и местные сопротивления.")
                        Text("• СНиП 2.04.02-84 — формула с табличными коэффициентами A₀, A₁, C, m " +
                                "для наружных водопроводов.")
                        Text("• Плотность и вязкость воды зависят от средней температуры. " +
                                "Шероховатость и местные сопротивления используются " +
                                "в методе «Теоретическая гидравлика».")
                        Text("• Зелёная зона на графике — рекомендуемая скорость воды " +
                                "0,25–1,5 м/с.")
                    }
                }
            )
        }
    }
}
