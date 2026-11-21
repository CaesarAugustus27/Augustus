package io.github.octaviusframework.form.control.type.selection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.ControlOrientation
import org.octavius.theme.FormSpacing

/**
 * A control for choosing several options from a group with checkboxes.
 */
class CheckboxGroupControl<T : Any>(
    label: String?,
    private val options: List<SelectionOption<T>>,
    private val orientation: ControlOrientation = ControlOrientation.VERTICAL,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<List<T>>>? = null
) : Control<List<T>>(
    label,
    required,
    dependencies,
    hasStandardLayout = true,
    actions = actions
) {

    override fun acceptsValue(value: Any) =
        value is List<*> && value.all { item -> options.any { it.value == item } }

    override fun copyInitToValue(value: List<T>): List<T> {
        return value.toList()
    }

    @Composable
    override fun Display(
        controlContext: ControlContext,
        controlState: ControlState<List<T>>,
        isRequired: Boolean
    ) {
        val scope = rememberSupervisedCoroutineScope()
        val currentList = controlState.value.value ?: emptyList()

        val content = @Composable {
            options.forEach { option ->
                val isChecked = currentList.contains(option.value)
                val rowModifier = if (orientation == ControlOrientation.VERTICAL) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                }

                Row(
                    modifier = rowModifier
                        .toggleable(
                            value = isChecked,
                            onValueChange = { checked ->
                                val newList = if (checked) {
                                    currentList + option.value
                                } else {
                                    currentList - option.value
                                }
                                controlState.value.value = newList
                                executeActions(controlContext, newList, scope)
                            },
                            role = Role.Checkbox
                        )
                        .padding(
                            vertical = FormSpacing.booleanRowPaddingVertical,
                            horizontal = FormSpacing.booleanRowPaddingHorizontal
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null // the click is handled by the Row (toggleable)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.displayText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (orientation == ControlOrientation.VERTICAL) {
            Column {
                content()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}
