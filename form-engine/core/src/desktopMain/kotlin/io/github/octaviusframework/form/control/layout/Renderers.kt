/**
 * Functions rendering the elements shared by controls.
 * Keeps labels, errors and other UI elements looking the same.
 */
package io.github.octaviusframework.form.control.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.octavius.theme.FormSpacing



/**
 * Renders a control's label with an asterisk for required fields.
 */
@Composable
private fun RenderLabel(label: String, isRequired: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium
    )

    if (isRequired) {
        Text(
            text = "*",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Renders the standard control label with padding.
 * Used by most form controls.
 */
@Composable
internal fun RenderNormalLabel(label: String?, isRequired: Boolean) {
    if (label == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            start = FormSpacing.labelPaddingStart,
            bottom = FormSpacing.labelPaddingBottom
        )
    ) {
        RenderLabel(label, isRequired)
    }
}

/**
 * Renders the label of a checkbox control without extra padding.
 * Checkboxes lay out their elements themselves.
 */
@Composable
internal fun RenderCheckboxLabel(label: String?, isRequired: Boolean) {
    if (label == null) return

    RenderLabel(label, isRequired)
}


/**
 * Renders a single field error from the ErrorManager.
 *
 * @param error error text to show
 */
@Composable
internal fun RenderFieldError(error: String) {
    Text(
        text = error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(
            start = FormSpacing.errorPaddingStart,
            bottom = FormSpacing.errorPaddingBottom
        )
    )
}