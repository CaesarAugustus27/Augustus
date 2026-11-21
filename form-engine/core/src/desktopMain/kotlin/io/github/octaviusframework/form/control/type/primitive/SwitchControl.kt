package io.github.octaviusframework.form.control.type.primitive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.RenderCheckboxLabel
import org.octavius.theme.FormSpacing

/**
 * A control for entering boolean values (true/false).
 *
 * Renders a standard Switch with a label. Used instead of a checkbox where changing the state is like
 * turning a feature on or off.
 */
class SwitchControl(
    label: String?,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<Boolean>>? = null,
) : Control<Boolean>(
    label,
    required = true,
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
                    val currentValue = controlState.value.value ?: false
                    // Force a non-null state, since a Switch has no indeterminate state
                    if (controlState.value.value == null) {
                        controlState.value.value = false
                    }

                    Switch(
                        checked = currentValue,
                        onCheckedChange = {
                            controlState.value.value = it
                            executeActions(controlContext, it, scope)
                        }
                    )
                    
                    RenderCheckboxLabel(label, isRequired)
                }

                DisplayFieldErrors(controlContext)
            }
        }
    }
}
