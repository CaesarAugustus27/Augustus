package io.github.octaviusframework.form.control.base

/**
 * Defines a dependency between controls.
 *
 * @param controlPath path to the control (relative or absolute)
 * @param value value of the control that activates the dependency
 * @param dependencyType type of the dependency (visibility or being required)
 * @param comparisonType how values are compared
 */
data class ControlDependency<T>(
    val controlPath: String,
    val value: T,
    val dependencyType: DependencyType,
    val comparisonType: ComparisonType
)

/**
 * Type of a dependency between controls.
 */
enum class DependencyType {
    /** The control is visible/invisible depending on another control's value */
    Visible,

    /** The control is required/not required depending on another control's value */
    Required,
}

/**
 * How values are compared in dependencies.
 */
enum class ComparisonType {
    /** The value must be one of the given values */
    OneOf,

    /** The value must equal the given value */
    Equals,

    /** The value must differ from the given value */
    NotEquals
}