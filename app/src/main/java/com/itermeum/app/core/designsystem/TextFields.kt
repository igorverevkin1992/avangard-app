package com.itermeum.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.itermeum.app.R
import com.itermeum.app.core.common.NoteFormat

/** D-69: the keyboard of every prose field starts sentences with a capital letter. */
val ProseKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/**
 * A text field for prose: sentences begin with a capital, the keyboard's
 * autocorrect stays on. Numbers, dates, phones and passwords keep their own fields.
 */
@Composable
fun ImTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = ProseKeyboard,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, label = label, supportingText = supportingText,
        trailingIcon = trailingIcon, singleLine = singleLine, minLines = minLines, isError = isError,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
    )
}

/**
 * D-69: a note — several lines, capitals, and plain lists: «• Список» and «1. Список»
 * turn the current line into an item, Enter continues the list, Enter on an empty item ends it.
 */
@Composable
fun ImNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    minLines: Int = 3,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // A change from outside (a draft loaded later) replaces the text; the cursor goes to its end.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    fun apply(edit: NoteFormat.Edit) {
        field = TextFieldValue(edit.text, TextRange(edit.cursor)); onValueChange(edit.text)
    }
    Column(modifier) {
        OutlinedTextField(
            value = field,
            onValueChange = { next ->
                val edit = NoteFormat.afterEnter(field.text, next.text, next.selection.start)
                if (edit != null) apply(edit) else { field = next; onValueChange(next.text) }
            },
            label = label, minLines = minLines, keyboardOptions = ProseKeyboard,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { apply(NoteFormat.toggleBullet(field.text, field.selection.start)) }) { Text(stringResource(R.string.note_bullets)) }
            TextButton(onClick = { apply(NoteFormat.toggleNumber(field.text, field.selection.start)) }) { Text(stringResource(R.string.note_numbers)) }
        }
    }
}
