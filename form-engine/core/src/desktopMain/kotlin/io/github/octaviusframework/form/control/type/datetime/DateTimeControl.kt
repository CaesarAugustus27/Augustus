package io.github.octaviusframework.form.control.type.datetime

import androidx.compose.runtime.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.validator.datetime.DateTimeValidator
import org.octavius.ui.datetime.DateTimePickerDialog
import org.octavius.ui.datetime.PickerTextField
import org.octavius.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DateTimeControl<T : Any>(
    private val adapter: DateTimeAdapter<T>,
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DateTimeValidation<T>? = null,
    actions: List<ControlAction<T>>? = null
) : Control<T>(label, required, dependencies, validationOptions, actions) {

    override val validator: ControlValidator<T> = DateTimeValidator(validationOptions, adapter)

    override fun acceptsValue(value: Any) = adapter.valueClass.isInstance(value)

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<T>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()
        var showDialog by remember { mutableStateOf(false) }

        PickerTextField(
            value = adapter.format(controlState.value.value),
            onClick = { showDialog = true },
            onClear = if (!isRequired) { {
                controlState.value.value = null
                executeActions(controlContext, null, scope)
            } } else null,
            isRequired = isRequired
        )

        if (showDialog) {
            DateTimePickerDialog(
                adapter = adapter,
                initialValue = controlState.value.value,
                onDismiss = { showDialog = false },
                onConfirm = { newValue ->
                    showDialog = false
                    controlState.value.value = newValue
                    executeActions(controlContext, newValue, scope)
                }
            )
        }
    }
}

// --- Factory functions for the concrete date/time types ---

/**
 * Factory function creating a control for picking dates (LocalDate).
 */
@Suppress("FunctionName")
fun DateControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DateTimeValidation<LocalDate>? = null,
    actions: List<ControlAction<LocalDate>>? = null
) = DateTimeControl(
    adapter = DateAdapter,
    label = label,
    required = required,
    dependencies = dependencies,
    validationOptions = validationOptions,
    actions = actions
)

/**
 * Factory function creating a control for picking a date and time (LocalDateTime).
 */
@Suppress("FunctionName")
fun LocalDateTimeControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DateTimeValidation<LocalDateTime>? = null,
    actions: List<ControlAction<LocalDateTime>>? = null
) = DateTimeControl(
    adapter = LocalDateTimeAdapter,
    label = label,
    required = required,
    dependencies = dependencies,
    validationOptions = validationOptions,
    actions = actions
)

/**
 * Factory function creating a control for picking instants (Instant).
 */
@OptIn(ExperimentalTime::class)
@Suppress("FunctionName")
fun InstantControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DateTimeValidation<Instant>? = null,
    actions: List<ControlAction<Instant>>? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) = DateTimeControl(
    adapter = InstantAdapter(timeZone),
    label = label,
    required = required,
    dependencies = dependencies,
    validationOptions = validationOptions,
    actions = actions
)

/**
 * Factory function creating a control for picking a time (LocalTime).
 */
@Suppress("FunctionName")
fun LocalTimeControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DateTimeValidation<LocalTime>? = null,
    actions: List<ControlAction<LocalTime>>? = null
) = DateTimeControl(
    adapter = LocalTimeAdapter,
    label = label,
    required = required,
    dependencies = dependencies,
    validationOptions = validationOptions,
    actions = actions
)
