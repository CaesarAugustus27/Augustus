package io.github.octaviusframework.form.control.repeatable

import io.github.octaviusframework.form.localization.FormTr

/**
 * Rules of a repeatable list: the number of rows, and [uniqueFields] - row fields whose values taken together
 * must not repeat in two rows. A row with any of them empty does not take part in that check.
 */
data class RepeatableValidation(
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val uniqueFields: List<String> = emptyList()
) {
    /**
     * @param fieldValue Current value of a field of a row, by the field's name.
     */
    internal fun errors(rows: List<RepeatableRow>, fieldValue: (row: RepeatableRow, field: String) -> Any?): List<String> =
        listOfNotNull(
            minItems?.takeIf { rows.size < it }?.let { FormTr.Validation.minimumItems(it) },
            maxItems?.takeIf { rows.size > it }?.let { FormTr.Validation.maximumItems(it) },
            firstDuplicate(rows, fieldValue)?.let { FormTr.Validation.duplicateInRow(it + 1) }
        )

    // Index of the first row that repeats the unique fields of an earlier one.
    private fun firstDuplicate(rows: List<RepeatableRow>, fieldValue: (RepeatableRow, String) -> Any?): Int? {
        if (uniqueFields.isEmpty()) return null
        val seen = mutableSetOf<List<Any?>>()
        for ((index, row) in rows.withIndex()) {
            val key = uniqueFields.map { field -> fieldValue(row, field) }
            if (key.any { it == null || (it is String && it.isBlank()) }) continue
            if (!seen.add(key)) return index
        }
        return null
    }
}
