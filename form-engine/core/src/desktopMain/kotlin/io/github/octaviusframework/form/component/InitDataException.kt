package io.github.octaviusframework.form.component

/**
 * Data from `initData` does not fit the form schema: a value lies under a path that points at no control, or
 * the control does not accept it (wrong type, value outside the options).
 *
 * @param path Path of the value in the form schema, e.g. `"basic_info/status"`, and for a field of list rows
 *   `"publications/status"`.
 */
class InitDataException(val path: String, reason: String) : RuntimeException("Initial data at '$path': $reason")
