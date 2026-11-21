package io.github.octaviusframework.form.control.base

import java.math.BigDecimal
import kotlin.time.Duration

/**
 * Validation options of form controls.
 * Defines validation rules that apply to the various control types.
 */
sealed class ValidationOptions

/**
 * Validation options of text fields.
 */
data class StringValidation(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: Regex? = null,
    val patternErrorMessage: String? = null
) : ValidationOptions()

/**
 * Validation options of integers.
 */
data class IntegerValidation(
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null
) : ValidationOptions()

/**
 * Validation options of floating-point numbers.
 */
data class DoubleValidation(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val decimalPlaces: Int? = null
) : ValidationOptions()

/**
 * Validation options of high-precision numbers (BigDecimal).
 */
data class BigDecimalValidation(
    val min: BigDecimal? = null,
    val max: BigDecimal? = null,
    val step: BigDecimal? = null,
    val decimalPlaces: Int? = null
) : ValidationOptions()

/**
 * Validation options of text lists.
 */
data class StringListValidation(
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val itemValidation: StringValidation? = null
) : ValidationOptions()

/**
 * Validation options of repeatable controls.
 */
data class RepeatableValidation(
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val uniqueFields: List<String> = emptyList()
) : ValidationOptions()

/**
 * Generic validation options of all date and time controls.
 */
data class DateTimeValidation<T>(
    val min: T? = null,
    val max: T? = null
) : ValidationOptions()


/**
 * Validation options of time intervals.
 */
data class IntervalValidation(
    val min: Duration? = null,
    val max: Duration? = null
) : ValidationOptions()