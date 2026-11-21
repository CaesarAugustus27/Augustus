package io.github.octaviusframework.form.control.type.datetime

import androidx.compose.runtime.*
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.validator.datetime.IntervalValidator
import org.octavius.ui.datetime.IntervalPickerDialog
import org.octavius.ui.datetime.PickerTextField
import kotlin.time.Duration

class IntervalControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: IntervalValidation? = null,
    actions: List<ControlAction<Duration>>? = null
) : Control<Duration>(
    label, required, dependencies, validationOptions, actions
) {
    override val validator: ControlValidator<Duration> = IntervalValidator(validationOptions)

    override fun acceptsValue(value: Any) = value is Duration

    private fun formatDuration(duration: Duration?): String {
        if (duration == null) return ""
        return duration.toComponents { hours, minutes, seconds, _ ->
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<Duration>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()
        var showDialog by remember { mutableStateOf(false) }

        PickerTextField(
            value = formatDuration(controlState.value.value),
            onClick = { showDialog = true },
            onClear = if (!isRequired) {
                {
                    controlState.value.value = null
                    executeActions(controlContext, null, scope)
                }
            } else null,
            isRequired = isRequired
        )

        if (showDialog) {
            IntervalPickerDialog(
                initialValue = controlState.value.value,
                onDismiss = { showDialog = false },
                onConfirm = { newDuration ->
                    controlState.value.value = newDuration
                    executeActions(controlContext, newDuration, scope)
                    showDialog = false
                }
            )
        }
    }
}