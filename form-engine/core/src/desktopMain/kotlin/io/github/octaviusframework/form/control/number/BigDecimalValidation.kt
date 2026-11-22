package io.github.octaviusframework.form.control.number

import io.github.octaviusframework.form.localization.FormTr
import java.math.BigDecimal

/**
 * Rules of a `BigDecimal` value. `step` requires a multiple of it.
 */
data class BigDecimalValidation(
    val min: BigDecimal? = null,
    val max: BigDecimal? = null,
    val step: BigDecimal? = null,
    val decimalPlaces: Int? = null
) {
    internal fun errors(value: BigDecimal): List<String> = listOfNotNull(
        min?.takeIf { value < it }?.let { FormTr.Validation.minValue(it) },
        max?.takeIf { value > it }?.let { FormTr.Validation.maxValue(it) },
        decimalPlaces?.takeIf { value.scale() > it }?.let { FormTr.Validation.maxDecimalPlaces(it) },
        step?.takeIf { value.remainder(it).signum() != 0 }?.let { FormTr.Validation.multipleOf(it) }
    )
}
