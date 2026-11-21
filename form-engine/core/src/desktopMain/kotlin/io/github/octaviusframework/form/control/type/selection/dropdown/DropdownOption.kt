package io.github.octaviusframework.form.control.type.selection.dropdown

/**
 * A single option of a dropdown list.
 *
 * Holds the actual value together with the text shown to the user, keeping business logic (the value)
 * apart from presentation (the text).
 *
 * @param T type of the value held by the option
 * @property value the option's actual value, used in application logic
 * @property displayText text shown to the user in the interface
 * @property payload Additional data that came with the option (e.g. the whole database row). On selection
 *   it goes to `ActionContext.payload`, so an action can fill other controls from it. It is absent when the
 *   form loads - the value then comes from the saved record, not from the menu.
 */
data class DropdownOption<T>(
    val value: T,
    val displayText: String,
    val payload: Any? = null
)