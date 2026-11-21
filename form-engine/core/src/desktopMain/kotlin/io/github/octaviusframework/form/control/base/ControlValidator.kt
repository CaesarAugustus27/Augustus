package io.github.octaviusframework.form.control.base

import io.github.octaviusframework.form.component.ErrorManager
import io.github.octaviusframework.form.component.FormSchema
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.component.PathResolver
import io.github.octaviusframework.form.localization.FormTr

/**
 * Abstract base class of all form control validators.
 *
 * A validator is responsible for:
 * - Checking control visibility based on dependencies
 * - Determining whether fields are required, depending on conditions
 * - Basic validation (required, empty state)
 * - Validation specific to the control type
 *
 * Every control type has its own validator extending this class and implementing validateSpecific() for
 * its specific rules.
 *
 * @param T type of the data held by the control
 */
abstract class ControlValidator<T : Any> {
    protected lateinit var formState: FormState
    protected lateinit var formSchema: FormSchema
    protected lateinit var errorManager: ErrorManager

    /**
     * Sets the form component references for the validator.
     */
    internal open fun setupFormReferences(
        formState: FormState,
        formSchema: FormSchema,
        errorManager: ErrorManager
    ) {
        this.formState = formState
        this.formSchema = formSchema
        this.errorManager = errorManager
    }

    private fun isDependencyMet(
        dependency: ControlDependency<*>,
        controlContext: ControlContext
    ): Boolean {
        // State from the parent class.
        val states = formState.getAllStates()

        val resolvedControlName = PathResolver.resolvePath(dependency.controlPath, controlContext)
        // A wrong path is a bug in the schema - a dependency silently met would hide it forever.
        val dependentState = states[resolvedControlName] ?: throw IllegalArgumentException(
            "Dependency of control '${controlContext.fullStatePath}' points at '${dependency.controlPath}' " +
                "(resolved to '$resolvedControlName'), but the form has no such control"
        )

        val dependentValue = dependentState.value.value

        return when (dependency.comparisonType) {
            ComparisonType.OneOf -> {
                @Suppress("UNCHECKED_CAST")
                val acceptedValues = dependency.value as? List<*> ?: listOf(dependency.value)
                dependentValue in acceptedValues
            }
            ComparisonType.Equals -> dependentValue == dependency.value
            ComparisonType.NotEquals -> dependentValue != dependency.value
        }
    }

    internal fun isControlVisible(
        control: Control<*>,
        controlContext: ControlContext
    ): Boolean {
        // Step 1: check the parent's visibility (recursively)
        controlContext.parent?.let { parentContext ->
            val parentControl = formSchema.getControl(parentContext.fullControlPath)!!
            if (!isControlVisible(parentControl, parentContext)) {
                return false
            }
        }

        // Step 2: check that ALL Visible dependencies are met.
        return control.dependencies
            ?.filter { it.value.dependencyType == DependencyType.Visible }
            ?.all { (_, dependency) -> isDependencyMet(dependency, controlContext) }
            ?: true // No dependencies means visible.
    }

    /**
     * Checks whether the control is required, based on its configuration and dependencies.
     *
     * The control is required when:
     * - It has required = true, or
     * - A Required dependency is met
     *
     * @param control the control to check
     * @return true if the control is required
     */
    internal fun isControlRequired(
        control: Control<*>,
        controlContext: ControlContext
    ): Boolean {
        // Basic condition: required if the definition says so.
        if (control.required == true) return true

        // Additional condition: required if ANY Required dependency is met.
        return control.dependencies
            ?.filter { it.value.dependencyType == DependencyType.Required }
            ?.any { (_, dependency) -> isDependencyMet(dependency, controlContext) }
            ?: false // No dependencies means not required (unless `required == true`).
    }

    /**
     * Checks whether the value is empty or not filled in.
     *
     * A value counts as empty when it:
     * - Is null
     * - Is an empty string or only whitespace
     * - Is an empty list
     *
     * @param value the value to check
     * @return true if the value is empty
     */
    internal fun isValueEmpty(value: Any?): Boolean {
        return when (value) {
            null -> true
            is String -> value.isBlank()
            is List<*> -> value.isEmpty()
            else -> false
        }
    }

    /**
     * Main validation method of a control.
     *
     * Validation:
     * 1. Checks whether the control is visible (if not, skips validation)
     * 2. Determines whether the control is required
     * 3. Checks that a required field is not empty
     * 4. Runs the validation specific to the control type
     *
     * @param state control state
     * @param control control definition
     */
    open fun validate(
        controlContext: ControlContext,
        state: ControlState<*>,
        control: Control<*>
    ) {
        // An invisible control skips validation
        if (!isControlVisible(control, controlContext)) {
            return
        }

        // Is the field required
        val isRequired = isControlRequired(control, controlContext)

        // A required field with an empty value gets an error
        if (isRequired && isValueEmpty(state.value.value)) {
            errorManager.setFieldErrors(controlContext.fullStatePath, listOf(FormTr.Validation.fieldRequired()))
            return
        }

        // Nothing more to check on null; acceptsValue keeps any other value a T.
        @Suppress("UNCHECKED_CAST")
        val value = state.value.value as T? ?: return
        validateSpecific(controlContext, value)
    }

    /**
     * Validation specific to the control type, run for a non-null value.
     *
     * Subclasses implement it for the validation of their control type (e.g. checking an email format,
     * a number range, uniqueness of values, etc.).
     *
     * The default implementation does no additional validation.
     *
     * @param controlContext control context
     * @param value the control's value
     */
    abstract fun validateSpecific(controlContext: ControlContext, value: T)
}