package io.github.octaviusframework.form.control.type.number

import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.DoubleValidation
import io.github.octaviusframework.form.control.type.number.primitive.PrimitiveNumberControl
import io.github.octaviusframework.form.control.validator.number.DoubleValidator

/**
 * A control for entering real (floating-point) numbers.
 * Extends PrimitiveNumberControl with parsing for Double.
 */
class DoubleControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: DoubleValidation? = null,
    actions: List<ControlAction<Double>>? = null
) : PrimitiveNumberControl<Double>(
    label,
    required,
    dependencies,
    validationOptions = validationOptions,
    actions = actions
) {
    override val validator: ControlValidator<Double> = DoubleValidator(validationOptions)

    override fun acceptsValue(value: Any) = value is Double

    override fun parseValue(text: String): Double? {
        val cleanText = text.replace(",", ".")
        val doubleValue = cleanText.toDoubleOrNull()
        return if (doubleValue != null && doubleValue.isFinite()) doubleValue else null
    }
}