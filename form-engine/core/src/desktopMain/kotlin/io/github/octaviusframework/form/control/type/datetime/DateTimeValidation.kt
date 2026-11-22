package io.github.octaviusframework.form.control.type.datetime

import io.github.octaviusframework.form.localization.FormTr

/**
 * Bounds of a date or time value, inclusive.
 */
data class DateTimeValidation<T : Comparable<T>>(
    val min: T? = null,
    val max: T? = null
) {
    /**
     * @param format How the control shows a value, used for the bound in the message.
     */
    internal fun errors(value: T, format: (T) -> String): List<String> = listOfNotNull(
        min?.takeIf { value < it }?.let { FormTr.Validation.minValue(format(it)) },
        max?.takeIf { value > it }?.let { FormTr.Validation.maxValue(format(it)) }
    )
}
