package com.itermeum.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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
        // «07» or «7:3» is not a time yet: mark it until four digits are typed.
        isError = text.count(Char::isDigit) in 1..3,
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

/** Drawn checkbox (glyphs like ■/□ render tiny in Cormorant): gold frame, accent fill and a tick when done. */
@Composable
fun ImCheck(done: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val frame = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.onPrimary
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val w = this.size.width
        if (done) drawRect(fill)
        drawRect(frame, style = Stroke(1.5.dp.toPx()))
        if (done) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.22f, w * 0.52f); lineTo(w * 0.42f, w * 0.72f); lineTo(w * 0.78f, w * 0.30f)
            }
            drawPath(path, tick, style = Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Square))
        }
    }
}

/**
 * One tappable line: optional check on the left, title and a short subtitle,
 * a gold › on the right. The basic building block of every hub screen.
 */
@Composable
fun ImNavRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, done: Boolean? = null) {
    ImPanel(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done != null) ImCheck(done, Modifier.padding(end = ImSpacing.card))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * A section that can be folded: header with a summary on the right
 * («3 из 5»), content only when open. Keeps long screens short.
 */
@Composable
fun ImCollapsible(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ImSpacing.grid)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = ImSpacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImCross(8.dp)
            Spacer(Modifier.width(ImSpacing.grid))
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(ImSpacing.grid))
            Hairline(Modifier.weight(1f))
            summary?.let {
                Spacer(Modifier.width(ImSpacing.grid))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(ImSpacing.tight))
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        if (open) content()
    }
}

