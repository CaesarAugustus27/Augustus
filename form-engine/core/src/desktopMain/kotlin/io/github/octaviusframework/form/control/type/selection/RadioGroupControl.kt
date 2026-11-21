package io.github.octaviusframework.form.control.type.selection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
 * A control for choosing one option from a group with radio buttons.
 */
class RadioGroupControl<T : Any>(
    label: String?,
    private val options: List<SelectionOption<T>>,
    private val orientation: ControlOrientation = ControlOrientation.VERTICAL,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<T>>? = null
) : Control<T>(
    label,
    required,
    dependencies,
    hasStandardLayout = true,
    actions = actions
) {

    override fun acceptsValue(value: Any) = options.any { it.value == value }

    @Composable
    override fun Display(
        controlContext: ControlContext,
        controlState: ControlState<T>,
        isRequired: Boolean
    ) {
        val scope = rememberSupervisedCoroutineScope()
        val currentValue = controlState.value.value

        val content = @Composable {
            options.forEach { option ->
                val isSelected = option.value == currentValue
                val rowModifier = if (orientation == ControlOrientation.VERTICAL) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                }

                Row(
                    modifier = rowModifier
                        .selectable(
                            selected = isSelected,
                            onClick = {
                                controlState.value.value = option.value
                                executeActions(controlContext, option.value, scope)
                            },
                            role = Role.RadioButton
                        )
                        .padding(
                            vertical = FormSpacing.booleanRowPaddingVertical,
                            horizontal = FormSpacing.booleanRowPaddingHorizontal
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null // the click is handled by the Row (selectable)
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
            Column(modifier = Modifier.selectableGroup()) {
                content()
            }
        } else {
            Row(
                modifier = Modifier.selectableGroup().fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}
