package io.github.octaviusframework.form.control.validator.number

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.DoubleValidation
import io.github.octaviusframework.form.localization.FormTr
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Validator of floating-point number controls, with validation options.
 */
class DoubleValidator(
    private val validationOptions: DoubleValidation? = null
) : ControlValidator<Double>() {

    override fun validateSpecific(controlContext: ControlContext, value: Double) {
        val errors = mutableListOf<String>()

        validationOptions?.let { options ->
            // Minimum value
            options.min?.let { min ->
                if (value < min) {
                    errors.add(FormTr.Validation.minValue(min))
                }
            }

            // Maximum value
            options.max?.let { max ->
                if (value > max) {
                    errors.add(FormTr.Validation.maxValue(max))
                }
            }

            // Decimal places
            options.decimalPlaces?.let { decimalPlaces ->
                val multiplier = 10.0.pow(decimalPlaces)
                val rounded = round(value * multiplier) / multiplier
                if (value != rounded) {
                    errors.add(FormTr.Validation.maxDecimalPlaces(decimalPlaces))
                }
            }

            // Step
            options.step?.let { step ->
                val remainder = value % step
                if (remainder != 0.0 && (remainder - step).let { abs(it) } > 1e-10) {
                    errors.add(FormTr.Validation.multipleOf(step))
                }
            }
        }

        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}