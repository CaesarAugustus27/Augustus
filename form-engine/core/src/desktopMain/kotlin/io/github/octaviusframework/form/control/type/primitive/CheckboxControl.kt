package io.github.octaviusframework.form.control.type.primitive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.RenderCheckboxLabel
import org.octavius.theme.FormSpacing

/**
 * A control for entering boolean values (true/false).
 *
 * Renders a checkbox with a label. Required fields use a standard checkbox (true/false), optional ones a
 * three-state checkbox (true/false/null). Cycles through the states on click.
 */
class CheckboxControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<Boolean>>? = null,
) : Control<Boolean>(
    label,
    required,
    dependencies,
    hasStandardLayout = false,
    actions = actions
) {
    override fun acceptsValue(value: Any) = value is Boolean

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<Boolean>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FormSpacing.booleanControlPadding),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = FormSpacing.booleanRowPaddingVertical,
                            horizontal = FormSpacing.booleanRowPaddingHorizontal
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRequired) {
                        val currentValue = controlState.value.value ?: false
                        controlState.value.value = currentValue
                        Checkbox(
                            checked = currentValue,
                            onCheckedChange = {
                                controlState.value.value = it
                                executeActions(controlContext, it, scope)
                            }
                        )
                    } else {
                        TriStateCheckbox(
                            state = controlState.value.value?.let { ToggleableState(it) }
                                ?: ToggleableState.Indeterminate,
                            onClick = {
                                val mapping = mapOf(null to false, false to true, true to null)
                                val newValue = mapping[controlState.value.value]
                                controlState.value.value = newValue
                                executeActions(controlContext, newValue, scope)
                            }
                        )
                    }
                    RenderCheckboxLabel(label, isRequired)
                }

                DisplayFieldErrors(controlContext)
            }
        }
    }
}