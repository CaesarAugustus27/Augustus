package io.github.octaviusframework.form.control.validator.datetime

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.IntervalValidation
import io.github.octaviusframework.form.localization.FormTr
import kotlin.time.Duration

class IntervalValidator(
    private val validationOptions: IntervalValidation? = null
) : ControlValidator<Duration>() {

    private fun formatDuration(duration: Duration): String {
        return duration.toComponents { hours, minutes, seconds, _ ->
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    override fun validateSpecific(controlContext: ControlContext, value: Duration) {
        val errors = mutableListOf<String>()

        validationOptions?.let { options ->
            options.min?.let { min ->
                if (value < min) {
                    errors.add(FormTr.Validation.minInterval(formatDuration(min)))
                }
            }
            options.max?.let { max ->
                if (value > max) {
                    errors.add(FormTr.Validation.maxInterval(formatDuration(max)))
                }
            }
        }
        errorManager.setFieldErrors(controlContext.fullStatePath, errors)
    }
}