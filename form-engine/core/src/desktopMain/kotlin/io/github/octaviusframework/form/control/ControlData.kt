package io.github.octaviusframework.form.control

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Reactive state of a single form control.
 *
 * Holds everything about the control's state needed for rendering, validation and change tracking, using
 * the Compose State API for automatic recomposition.
 *
 * @param T Type of the data held by the control.
 * @param value Current value of the control (edited by the user) - MutableState.
 * @param initValue Original value loaded from the database or set as a default - MutableState.
 * @param displayText Text shown to the user when it cannot be derived from [value] on every render: the
 *                    edit buffer of a number field (`1.` is not a number yet) or the label of the selected
 *                    dropdown item (needs a query). `null` means "not determined" - the control fills it
 *                    in itself, synchronously or with a query. That is why `updateControl` from outside
 *                    clears this field: writing the value invalidates the text, and the clearing itself is
 *                    the signal to work it out again.
 * @param labelOverride Label overridden from an action, in place of the one from the schema.
 */
data class ControlState<T>(
    val value: MutableState<T?> = mutableStateOf(null),
    val initValue: MutableState<T?> = mutableStateOf(null),
    val displayText: MutableState<String?> = mutableStateOf(null),
    val labelOverride: MutableState<String?> = mutableStateOf(null)
)

/**
 * Rendering and state context of a control in the form hierarchy.
 *
 * @param localName Name of the control in its immediate context (e.g. "firstName", "publications").
 * @param statePath Path to the parent container (e.g. "user", "publications[uuid]") for the state.
 * @param controlPath Path to the parent container (e.g. "user", "publications") for the controls
 * @param parent  Rendering context of the parent control. Allows navigating up the tree.
 */
data class ControlContext(
    val localName: String,
    val statePath: String = "",
    val controlPath: String = "",
    val parent: ControlContext? = null
) {
    val fullControlPath: String by lazy {
        if (controlPath.isEmpty()) localName else "$controlPath/$localName"
    }

    val fullStatePath: String by lazy {
        if (statePath.isEmpty()) localName else "$statePath/$localName"
    }

    /**
     * Creates the context of a child control in a section. A section is one data level - like a repeatable
     * row, only without `[rowId]` - so the child lies under `section/name`.
     */
    fun forSectionChild(childLocalName: String): ControlContext {
        return ControlContext(
            localName = childLocalName,
            statePath = fullStatePath,
            controlPath = fullControlPath,
            parent = this
        )
    }

    /**
     * Creates the context of a child control in a repeatable container.
     */
    fun forRepeatableChild(childLocalName: String, rowId: String): ControlContext {
        return ControlContext(
            localName = childLocalName,
            statePath = "$fullStatePath[$rowId]",
            controlPath = fullControlPath,
            parent = this
        )
    }
}
