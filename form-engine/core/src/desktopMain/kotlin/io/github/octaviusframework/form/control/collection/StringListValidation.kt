package io.github.octaviusframework.form.control.collection

import io.github.octaviusframework.form.control.primitive.StringValidation
import io.github.octaviusframework.form.localization.FormTr

/**
 * Rules of a text list: the number of items and, through [itemValidation], the rules of each item.
 */
data class StringListValidation(
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val itemValidation: StringValidation? = null
) {
    internal fun errors(value: List<String>): List<String> {
        val countErrors = listOfNotNull(
            minItems?.takeIf { value.size < it }?.let { FormTr.Validation.minimumItems(it) },
            maxItems?.takeIf { value.size > it }?.let { FormTr.Validation.maximumItems(it) }
        )
        val itemErrors = itemValidation?.let { rules ->
            value.flatMapIndexed { index, item ->
                rules.errors(item).map { FormTr.Validation.itemError(item = index + 1, message = it) }
            }
        }
        return countErrors + itemErrors.orEmpty()
    }
}
