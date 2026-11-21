package io.github.octaviusframework.form.control.type.button

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import org.octavius.theme.FormSpacing

/**
 * A control rendering a button whose only purpose is to run its actions.
 *
 * It is a "stateless" control (it uses the `Unit` type), holds no value and is not bound to a database
 * column. It is a UI element for triggering business logic, such as API calls, opening dialogs or
 * modifying other controls of the form.
 *
 * @param text Text shown on the button.
 * @param buttonType Visual style of the button (FILLED by default).
 * @param dependencies Visibility dependencies on other controls.
 * @param actions Actions to run on click. Must contain at least one action.
 */
class ButtonControl(
    val text: String,
    val buttonType: ButtonType = ButtonType.Filled,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<Unit>> // Actions are the point, so they are not optional
) : Control<Unit>(
    label = null, // Buttons have no label, the text is on them
    required = false, // Not applicable
    dependencies = dependencies,
    hasStandardLayout = false, // Full control over rendering
    actions = actions
) {
    override fun acceptsValue(value: Any) = false

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<Unit>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()

        val modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = FormSpacing.fieldPaddingVertical,
                horizontal = FormSpacing.fieldPaddingHorizontal
            )

        when (buttonType) {
            ButtonType.Filled -> {
                Button(
                    onClick = { executeActions(controlContext, Unit, scope) },
                    modifier = modifier
                ) {
                    Text(text)
                }
            }
            ButtonType.Outlined -> {
                OutlinedButton(
                    onClick = { executeActions(controlContext, Unit, scope) },
                    modifier = modifier
                ) {
                    Text(text)
                }
            }
            ButtonType.Text -> {
                TextButton(
                    onClick = { executeActions(controlContext, Unit, scope) },
                    modifier = modifier
                ) {
                    Text(text)
                }
            }
        }
    }
}