package io.github.octaviusframework.form.control.type.number.primitive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.theme.FormSpacing

/**
 * Abstract base class of number controls (Integer, Double, etc.).
 * Encapsulates the shared logic of rendering the text field, parsing and handling format errors.
 */
abstract class PrimitiveNumberControl<T : Number>(
    label: String?,
    required: Boolean?,
    dependencies: Map<String, ControlDependency<*>>?,
    validationOptions: ValidationOptions?,
    actions: List<ControlAction<T>>?
) : Control<T>(
    label,
    required,
    dependencies,
    validationOptions = validationOptions,
    actions = actions
) {
    /**
     * Parses the text into the target number type. Every concrete implementation provides it.
     * @param text Value of the text field.
     * @return The parsed value, or null on a format error.
     */
    protected abstract fun parseValue(text: String): T?

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<T>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()

        // The text buffer has to be separate from the value, because neither "1." nor "007" comes back from
        // toString() of the parsed number. It lives in ControlState, not in remember, so that updateControl
        // from outside can invalidate it - null means "stale", so the text is derived from the value.
        val textValue = controlState.displayText.value ?: (controlState.value.value?.toString() ?: "")

        // Invalidating the text from outside also clears the format error - otherwise the user is left with a
        // message about input they no longer see.
        LaunchedEffect(controlState.displayText.value) {
            if (controlState.displayText.value == null) {
                errorManager.setFormatError(controlContext.fullStatePath, null)
            }
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                controlState.displayText.value = newText

                if (newText.isEmpty()) {
                    if (controlState.value.value != null) {
                        controlState.value.value = null
                        executeActions(controlContext, null, scope)
                    }
                    errorManager.setFormatError(controlContext.fullStatePath, null)
                } else {
                    val parsed = parseValue(newText)
                    if (parsed != null) {
                        if (controlState.value.value != parsed) {
                            controlState.value.value = parsed
                            executeActions(controlContext, parsed, scope)
                        }
                        errorManager.setFormatError(controlContext.fullStatePath, null)
                    } else {
                        errorManager.setFormatError(controlContext.fullStatePath, FormTr.Validation.invalidNumberFormat())
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(
                vertical = FormSpacing.fieldPaddingVertical,
                horizontal = FormSpacing.fieldPaddingHorizontal
            ),
            singleLine = true
        )
    }
}