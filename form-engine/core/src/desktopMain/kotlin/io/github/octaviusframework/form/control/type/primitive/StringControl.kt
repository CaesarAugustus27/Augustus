package io.github.octaviusframework.form.control.type.primitive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import org.octavius.theme.FormSpacing

/**
 * A control for entering single-line text.
 *
 * Renders a standard text field with a label and validation. Supports all standard control features such
 * as dependencies, being required and error messages.
 */
class StringControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    private val validationOptions: StringValidation? = null,
    actions: List<ControlAction<String>>? = null
) : Control<String>(
    label,
    required,
    dependencies,
    actions = actions
) {
    override fun validationErrors(controlContext: ControlContext, value: String) = validationOptions?.errors(value).orEmpty()

    override fun acceptsValue(value: Any) = value is String


    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<String>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()

        OutlinedTextField(
            value = controlState.value.value.orEmpty(),
            onValueChange = {
                controlState.value.value = it
                executeActions(controlContext, it, scope)
            },
            modifier = Modifier.fillMaxWidth().padding(
                vertical = FormSpacing.fieldPaddingVertical,
                horizontal = FormSpacing.fieldPaddingHorizontal
            ),
            singleLine = true
        )
    }
}