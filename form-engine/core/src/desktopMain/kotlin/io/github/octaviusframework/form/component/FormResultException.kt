package io.github.octaviusframework.form.component

/**
 * Reading form results (`getCurrent`, `getCurrentAs` and their initial-value counterparts) failed: the path does
 * not point at a control, leads through a control that is not a section, or the value is not of the requested
 * type.
 *
 * @param path The path that was read, e.g. `"basic_info/status"`.
 */
class FormResultException(val path: String, reason: String) : RuntimeException("Form result at '$path': $reason")
