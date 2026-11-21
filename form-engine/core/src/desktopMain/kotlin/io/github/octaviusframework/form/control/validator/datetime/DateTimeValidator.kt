package io.github.octaviusframework.form.control.validator.datetime

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.DateTimeValidation
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.util.DateTimeAdapter

class DateTimeValidator<T: Any>(
    private val validationOptions: DateTimeValidation<T>? = null,
    private val adapter: DateTimeAdapter<T>
) : ControlValidator<T>() {

    override fun validateSpecific(controlContext: ControlContext, value: T) {
        val errors = mutableListOf<String>()

        validationOptions?.let { options ->
            options.min?.let { min ->
                // Standard comparison, assuming the T types are Comparable
                @Suppress("UNCHECKED_CAST")
                if ((value as Comparable<T>) < min) {
                    errors.add(FormTr.Validation.minValue(adapter.format(min)))
                }
            }
            options.max?.let { max ->
                @Suppress("UNCHECKED_CAST")
                if ((value as Comparable<T>) > max) {
                    errors.add(FormTr.Validation.maxValue(adapter.format(max)))
                }
            }
        }
        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}