package io.github.octaviusframework.form.control.type.number

import io.github.octaviusframework.form.localization.FormTr
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Rules of a floating-point value. `step` requires a multiple of it.
 */
data class DoubleValidation(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val decimalPlaces: Int? = null
) {
    internal fun errors(value: Double): List<String> = listOfNotNull(
        min?.takeIf { value < it }?.let { FormTr.Validation.minValue(it) },
        max?.takeIf { value > it }?.let { FormTr.Validation.maxValue(it) },
        decimalPlaces?.takeIf { !hasAtMostDecimalPlaces(value, it) }?.let { FormTr.Validation.maxDecimalPlaces(it) },
        step?.takeIf { !isMultipleOf(value, it) }?.let { FormTr.Validation.multipleOf(it) }
    )

    private fun hasAtMostDecimalPlaces(value: Double, places: Int): Boolean {
        val multiplier = 10.0.pow(places)
        return round(value * multiplier) / multiplier == value
    }

    // The quotient of a multiple can miss a whole number by rounding noise: 0.3 / 0.1 is 2.9999999999999996.
    private fun isMultipleOf(value: Double, step: Double): Boolean {
        val quotient = value / step
        return abs(quotient - round(quotient)) < 1e-9
    }
}
