package com.itermeum.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Charter panel (D-23): surface fill, 1dp stone outline and a gold-dim
 * hairline inset 3dp, like a ruled parchment frame. No elevation, no rounding.
 */
@Composable
fun ImPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val inner = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .drawBehind {
                val inset = 3.dp.toPx()
                drawRect(inner, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1f))
            }
            .padding(ImSpacing.card),
        content = content,
    )
}

/** Small-caps section label: cross pattée, label, hairline to the edge. */
@Composable
fun ImSectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = ImSpacing.tight), verticalAlignment = Alignment.CenterVertically) {
        ImCross(8.dp)
        Spacer(Modifier.width(ImSpacing.grid))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(ImSpacing.grid))
        Hairline(Modifier.weight(1f))
    }
}

/** `HH:mm` text field for a LocalTime; invalid input keeps the previous value. */
@Composable
fun ImTimeField(label: String, value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            // Digits only; the colon is inserted after the hour so «1800» reads as 18:00.
            val digits = t.filter { it.isDigit() }.take(4)
            text = if (digits.length <= 2) digits else digits.substring(0, 2) + ":" + digits.substring(2)
            if (digits.length == 4) {
                val h = digits.substring(0, 2).toInt(); val m = digits.substring(2).toInt()
                if (h in 0..23 && m in 0..59) onChange(LocalTime.of(h, m))
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ImMonoFamily),
    )
}

/** Primary full-width action, 56dp: stone fill, gold border, parchment capitals. */
@Composable
fun ImPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ImSpacing.touchPrimary),
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer, contentColor = scheme.onSurface,
            disabledContainerColor = scheme.surface, disabledContentColor = scheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.5.dp, if (enabled) scheme.tertiary else scheme.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary outlined action, 48dp: gold-dim border, parchment capitals. */
@Composable
fun ImSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ImSpacing.touch),
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface, disabledContentColor = scheme.onSurfaceVariant),
        border = BorderStroke(1.dp, if (enabled) scheme.outlineVariant else scheme.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Screen header with an optional back arrow and a ruled line beneath. */
@Composable
fun ImHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Назад" }) { Text("←", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary) }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            trailing()
        }
        ImRule()
    }
}

/**
 * Integer setting with − / + (replaces free text fields that coerced on every
 * keystroke). [step] per tap; the value is always inside [range].
 */
@Composable
fun ImStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier, step: Int = 1, suffix: String = "") {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "$label: меньше" }) { Text("−", style = MaterialTheme.typography.headlineMedium) }
        Text("$value$suffix", style = MaterialTheme.typography.titleLarge.copy(fontFamily = ImMonoFamily))
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "$label: больше" }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
    }
}
