package com.itermeum.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Flat panel: surface fill, 1dp outline, no elevation, no rounding beyond 2dp. */
@Composable
fun ImPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(ImSpacing.card),
        content = content,
    )
}

/** Small-caps section label in the display family. */
@Composable
fun ImSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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

/** Primary full-width action, 56dp. */
@Composable
fun ImPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ImSpacing.touchPrimary),
        shape = RoundedCornerShape(2.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary outlined action, 48dp. */
@Composable
fun ImSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ImSpacing.touch),
        shape = RoundedCornerShape(2.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Screen header with an optional back arrow. */
@Composable
fun ImHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.headlineMedium) }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}
