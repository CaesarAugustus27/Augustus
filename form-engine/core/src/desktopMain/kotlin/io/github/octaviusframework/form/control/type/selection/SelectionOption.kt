package io.github.octaviusframework.form.control.type.selection

/**
 * A single option of a grouping control.
 *
 * @param T type of the value held by the option
 * @property value the option's actual value, used in application logic
 * @property displayText text shown to the user in the interface
 */
data class SelectionOption<T>(
    val value: T,
    val displayText: String
)
