package io.github.octaviusframework.form.control.type.datetime

import io.github.octaviusframework.form.localization.FormTr
import kotlin.time.Duration

/**
 * Bounds of a time interval, inclusive.
 */
data class IntervalValidation(
    val min: Duration? = null,
    val max: Duration? = null
) {
    /**
     * @param format How the control shows an interval, used for the bound in the message.
     */
    internal fun errors(value: Duration, format: (Duration) -> String): List<String> = listOfNotNull(
        min?.takeIf { value < it }?.let { FormTr.Validation.minInterval(format(it)) },
        max?.takeIf { value > it }?.let { FormTr.Validation.maxInterval(format(it)) }
    )
}
