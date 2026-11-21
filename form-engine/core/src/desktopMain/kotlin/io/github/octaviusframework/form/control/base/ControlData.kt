package io.github.octaviusframework.form.control.base

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.octaviusframework.form.component.FormResultException
import io.github.octaviusframework.form.component.PathResolver
import kotlin.reflect.typeOf

/**
 * Result of processing a single form control.
 *
 * Holds both the current value (modified by the user) and the initial value (from the database or a
 * default) for comparisons.
 *
 * @param currentValue Current value of the control, processed by getResult.
 * @param initialValue Original value loaded from the database or set as a default.
 */
data class ControlResultData(
    val currentValue: Any?,
    val initialValue: Any?
)

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

/**
 * Alias for the map holding the results of all form controls.
 *
 * The key is the control name, the value is ControlResultData with the current and initial value. The main
 * data type passed to validation, save and action logic of the form.
 *
 * A section is a separate level: its result carries the map of its children's results, so a field in a
 * section is read by path, e.g. `getCurrent("basic_info/name")`.
 */
typealias FormResultData = Map<String, ControlResultData>

/**
 * Returns the current value of a control as type `T`.
 *
 * Usage: `formData.getCurrentAs<String>("basic_info/name")`
 *
 * @throws FormResultException when the value is not a `T` - also when it is `null` and `T` is not nullable, as
 *   for a hidden control.
 */
inline fun <reified T> FormResultData.getCurrentAs(path: String): T {
    return resultAs(path, getCurrent(path))
}

/**
 * Returns the current value of a control as Any?. A field of a hidden section has `null`, like every hidden control.
 */
fun FormResultData.getCurrent(path: String): Any? {
    return resultAt(path) { it.currentValue }?.currentValue
}

/**
 * Returns the initial value of a control as type `T`.
 *
 * Usage: `rowData.getInitialAs<Int>("id")`
 *
 * @throws FormResultException when the value is not a `T` - also when it is `null` and `T` is not nullable.
 */
inline fun <reified T> FormResultData.getInitialAs(path: String): T {
    return resultAs(path, getInitial(path))
}

/**
 * Returns the initial value of a control as Any?. Hiding does not change the initial value, also in a
 * hidden section.
 *
 * Usage: `rowData.getInitial("id")`
 */
fun FormResultData.getInitial(path: String): Any? {
    return resultAt(path) { it.initialValue }?.initialValue
}

// Walks down the path through sections, in each taking the children's result map from the value picked by
// [levelOf]. `null` means there was a hidden section on the way, and its fields have no current value.
private fun FormResultData.resultAt(path: String, levelOf: (ControlResultData) -> Any?): ControlResultData? {
    val names = path.split(PathResolver.SEPARATOR)
    var level: FormResultData = this
    for (name in names.dropLast(1)) {
        val children = levelOf(level.resultNamed(name, path)) ?: return null
        if (children !is Map<*, *>) throw FormResultException(path, "'$name' is not a section")
        @Suppress("UNCHECKED_CAST")
        level = children as FormResultData
    }
    return level.resultNamed(names.last(), path)
}

private fun FormResultData.resultNamed(name: String, path: String): ControlResultData {
    return this[name] ?: throw FormResultException(path, "no control '$name'")
}

/**
 * [value] read from [path] as [T], or [FormResultException] when it is not one.
 */
@PublishedApi
internal inline fun <reified T> resultAs(path: String, value: Any?): T {
    if (value is T) return value
    throw FormResultException(path, "expected ${typeOf<T>()}, got ${describeValue(value)}")
}

/**
 * A value for exception messages: `'text' (Type)`, cut to 100 characters, or `null`.
 */
@PublishedApi
internal fun describeValue(value: Any?): String {
    if (value == null) return "null"
    val shown = value.toString().let { if (it.length > 100) it.take(100) + "…" else it }
    return "'$shown' (${value::class.simpleName})"
}