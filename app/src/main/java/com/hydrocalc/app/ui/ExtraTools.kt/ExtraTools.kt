package com.hydrocalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Дополнительные инструменты расчёта (ExtraTools).
 *
 * Галочка «Потери на местные сопротивления 5 %» — для методов
 * Шевелева и СНиП, где рассчитываются только потери на трение
 * по длине трубы. Местные сопротивления (повороты, арматура,
 * фитинги) учитываются надбавкой в процентах от потерь на трение.
 *
 * В методе «Теория» галочка не показывается: там местные потери
 * уже считаются точно через сумму коэффициентов Σζ.
 */
@Composable
fun LocalLossesCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(4.dp))
            Text(
                "Потери на местные сопротивления 5%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (checked) {
            Text(
                "К потерям на трение добавляется +5%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 44.dp, top = 2.dp)
            )
        }
    }
}
