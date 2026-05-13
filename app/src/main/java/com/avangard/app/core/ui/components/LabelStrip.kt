package com.avangard.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.avangard.app.ui.theme.IsaColors

/**
 * `01·ГЕНЕРАЦИИ` style identification strip used at the head of module cards.
 * Trailing slot reserved for the [StatusBadge].
 */
@Composable
fun LabelStrip(
    code: String,
    name: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$code·$name",
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.titleLarge,
        )
        trailing?.invoke()
    }
}
