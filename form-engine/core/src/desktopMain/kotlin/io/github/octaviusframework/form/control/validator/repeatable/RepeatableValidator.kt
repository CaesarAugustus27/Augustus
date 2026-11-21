package io.github.octaviusframework.form.control.validator.repeatable

import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlValidator
import io.github.octaviusframework.form.control.base.RepeatableValidation
import io.github.octaviusframework.form.control.type.repeatable.RepeatableRow
import io.github.octaviusframework.form.localization.FormTr

/**
 * Validator of RepeatableControl.
 *
 * Validates controls that let the user add many rows of data (e.g. a list of authors, tags, categories).
 *
 * Features:
 * - Checking that values in given fields are unique
 * - Validating the minimum and maximum number of items
 *
 * It does not validate the rows' fields - the engine walks down to them itself through `childContexts`, as
 * to the children of a section.
 */
class RepeatableValidator(
    private val validationOptions: RepeatableValidation? = null
) : ControlValidator<List<RepeatableRow>>() {

    /**
     * Validates that values are unique across the rows of a repeatable control.
     * Works on the global form state.
     *
     * Algorithm:
     * 1. Takes all rows of the control
     * 2. Builds a uniqueness key for every row from the values of the given fields
     * 3. Checks whether the key has already been seen
     * 4. On a duplicate, sets an error with the row number
     *
     * The check runs only when uniqueFields is not empty.
     *
     * @param controlContext control name with the row context (needed to build hierarchical names)
     * @param value the rows of the repeatable control
     */
    override fun validateSpecific(controlContext: ControlContext, value: List<RepeatableRow>) {
        // 1. Rules of the list itself
        val allErrors = mutableListOf<String>()

        validationOptions?.let { options ->
            // min/max
            allErrors.addAll(validateListRules(value, options))

            // Uniqueness
            validateUniqueness(value, options, controlContext)?.let { uniquenessError ->
                allErrors.add(uniquenessError)
            }
        }

        // 2. Set the errors on the MAIN control
        errorManager.setFieldErrors(controlContext.fullStatePath, allErrors)
    }

    private fun validateUniqueness(
        rows: List<RepeatableRow>,
        options: RepeatableValidation,
        controlContext: ControlContext
    ): String? { // Returns the error string or null
        if (options.uniqueFields.isEmpty()) {
            return null
        }

        val allStates = formState.getAllStates()
        val seenValues = mutableSetOf<List<Any?>>()

        for ((index, row) in rows.withIndex()) {
            // Only rows without errors in the unique fields count
            val uniqueKey = options.uniqueFields.map { field ->
                val hierarchicalContext = controlContext.forRepeatableChild(field, row.id)
                allStates[hierarchicalContext.fullStatePath]?.value?.value
            }

            // Ignore keys with empty fields
            if (uniqueKey.any { it == null || (it is String && it.isBlank()) }) {
                continue
            }

            if (!seenValues.add(uniqueKey)) {
                // Duplicate found, return the error and stop
                return FormTr.Validation.duplicateInRow(index + 1)
            }
        }

        return null // No duplicates
    }

    private fun validateListRules(
        rows: List<RepeatableRow>,
        options: RepeatableValidation
    ): List<String> {
        val errors = mutableListOf<String>()

        // Minimum number of items
        options.minItems?.let { minItems ->
            if (rows.size < minItems) {
                errors.add(FormTr.Validation.minItems(minItems))
            }
        }

        // Maximum number of items
        options.maxItems?.let { maxItems ->
            if (rows.size > maxItems) {
                errors.add(FormTr.Validation.maxItems(maxItems))
            }
        }

        return errors
    }
}