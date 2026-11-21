package io.github.octaviusframework.form.control.validator.number

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.IntegerValidation
import io.github.octaviusframework.form.localization.FormTr

/**
 * Validator of integer controls, with validation options.
 */
class IntegerValidator(
    private val validationOptions: IntegerValidation? = null
) : ControlValidator<Int>() {

    override fun validateSpecific(controlContext: ControlContext, value: Int) {
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

            // Step
            options.step?.let { step ->
                if (value % step != 0) {
                    errors.add(FormTr.Validation.multipleOf(step))
                }
            }
        }

        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}