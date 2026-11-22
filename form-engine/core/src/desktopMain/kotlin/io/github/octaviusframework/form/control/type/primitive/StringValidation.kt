package io.github.octaviusframework.form.control.type.primitive

import io.github.octaviusframework.form.localization.FormTr

/**
 * Rules of a text value. A blank value passes them: whether it may be blank is decided by `required`.
 */
data class StringValidation(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: Regex? = null,
    val patternErrorMessage: String? = null
) {
    internal fun errors(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return listOfNotNull(
            minLength?.takeIf { value.length < it }?.let { FormTr.Validation.minLength(it) },
            maxLength?.takeIf { value.length > it }?.let { FormTr.Validation.maxLength(it) },
            pattern?.takeIf { !it.matches(value) }?.let { patternErrorMessage ?: FormTr.Validation.invalidFormat() }
        )
    }
}
