package io.github.octaviusframework.form.control.number

import io.github.octaviusframework.form.localization.FormTr

/**
 * Rules of an integer value. `step` requires a multiple of it.
 */
data class IntegerValidation(
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null
) {
    internal fun errors(value: Int): List<String> = listOfNotNull(
        min?.takeIf { value < it }?.let { FormTr.Validation.minValue(it) },
        max?.takeIf { value > it }?.let { FormTr.Validation.maxValue(it) },
        step?.takeIf { value % it != 0 }?.let { FormTr.Validation.multipleOf(it) }
    )
}
