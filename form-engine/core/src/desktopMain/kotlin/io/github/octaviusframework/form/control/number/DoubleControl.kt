package io.github.octaviusframework.form.control.number

import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.ControlContext
import io.github.octaviusframework.form.control.ControlDependency

/**
 * A control for entering real (floating-point) numbers.
 * Extends PrimitiveNumberControl with parsing for Double.
 */
class DoubleControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    private val validationOptions: DoubleValidation? = null,
    actions: List<ControlAction<Double>>? = null
) : PrimitiveNumberControl<Double>(
    label,
    required,
    dependencies,
    actions = actions
) {
    override fun validationErrors(controlContext: ControlContext, value: Double) = validationOptions?.errors(value).orEmpty()

    override fun acceptsValue(value: Any) = value is Double

    override fun parseValue(text: String): Double? {
        val cleanText = text.replace(",", ".")
        val doubleValue = cleanText.toDoubleOrNull()
        return if (doubleValue != null && doubleValue.isFinite()) doubleValue else null
    }
}