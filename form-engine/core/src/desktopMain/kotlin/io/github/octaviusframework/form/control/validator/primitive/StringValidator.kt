package io.github.octaviusframework.form.control.validator.primitive

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.StringValidation
import io.github.octaviusframework.form.localization.FormTr

/**
 * Validator of text controls, with validation options.
 */
class StringValidator(
    private val validationOptions: StringValidation? = null
) : ControlValidator<String>() {

    override fun validateSpecific(controlContext: ControlContext, value: String) {
        val errors = mutableListOf<String>()

        if (value.isBlank()) {
            errorManager.setFieldErrors(controlContext.fullStatePath, errors)
            return
        }

        validationOptions?.let { options ->
            // Minimum length
            options.minLength?.let { minLength ->
                if (value.length < minLength) {
                    errors.add(FormTr.Validation.minLength(minLength))
                }
            }

            // Maximum length
            options.maxLength?.let { maxLength ->
                if (value.length > maxLength) {
                    errors.add(FormTr.Validation.maxLength(maxLength))
                }
            }

            // Pattern
            options.pattern?.let { pattern ->
                if (!pattern.matches(value)) {
                    errors.add(options.patternErrorMessage ?: FormTr.Validation.invalidFormat())
                }
            }
        }

        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}