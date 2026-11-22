package io.github.octaviusframework.form

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
