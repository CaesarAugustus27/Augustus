package io.github.octaviusframework.form.control.validator.number

import io.github.octaviusframework.form.control.base.BigDecimalValidation
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.localization.FormTr
import java.math.BigDecimal

/**
 * Validator of high-precision number (BigDecimal) controls, with validation options.
 */
class BigDecimalValidator(
    private val validationOptions: BigDecimalValidation? = null
) : ControlValidator<BigDecimal>() {

    override fun validateSpecific(controlContext: ControlContext, value: BigDecimal) {
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
                if (value.scale() > decimalPlaces) {
                    errors.add(FormTr.Validation.maxDecimalPlaces(decimalPlaces))
                }
            }

            // Step
            options.step?.let { step ->
                if (value.remainder(step).compareTo(BigDecimal.ZERO) != 0) {
                    errors.add(FormTr.Validation.multipleOf(step))
                }
            }
        }

        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}