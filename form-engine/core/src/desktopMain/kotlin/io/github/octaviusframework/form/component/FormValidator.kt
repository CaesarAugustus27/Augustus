package io.github.octaviusframework.form.component

import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.FormResultData

/**
 * Validates the form on three levels:
 * 1. Field validation - checks required fields, format, dependencies between controls
 * 2. Business-rule validation - checks custom, domain-specific rules
 * 3. Action-specific validation - additional rules for particular buttons
 * Operates on the form state
 *
 * Extend the class to implement custom validation rules.
 */
open class FormValidator {

    protected lateinit var formState: FormState
    protected lateinit var formSchema: FormSchema
    protected lateinit var errorManager: ErrorManager

    internal fun setupFormReferences(formState: FormState, formSchema: FormSchema, errorManager: ErrorManager) {
        this.formState = formState
        this.formSchema = formSchema
        this.errorManager = errorManager
    }

    /**
     * Validates all form fields.
     *
     * Validation:
     * 1. Clears previous errors
     * 2. Runs each control's validation through its validator
     * 3. Checks required fields, format, dependencies
     *
     * @return true if all fields are valid
     */
    internal fun validateFields(): Boolean {

        formSchema.rootContexts().forEach { (controlContext, control) -> validateTree(controlContext, control) }

        // Check whether there are any field errors
        return !errorManager.hasFieldErrors() && !errorManager.hasFormatErrors()
    }

    // Every control validates itself, in a context that knows its parent, so a child of a hidden section or
    // of a hidden repeatable skips validation by itself - it checks visibility up the tree.
    private fun validateTree(controlContext: ControlContext, control: Control<*>) {
        control.validateControl(controlContext, formState.getControlState(controlContext.fullStatePath)!!)
        control.childContexts(controlContext).forEach { (childContext, child) -> validateTree(childContext, child) }
    }

    /**
     * Validates domain-specific business rules.
     *
     * The default implementation always returns true. Subclasses override it to implement custom rules
     * (e.g. checking for duplicates, verifying relations between fields, etc.)
     *
     * @param formResultData data collected from the form
     * @return true if the business rules are satisfied
     */
    open fun validateBusinessRules(formResultData: FormResultData): Boolean {
        return true
    }


    /**
     * Defines validation logic specific to individual form actions.
     * This validation ALWAYS runs for the given action, regardless of the `validates` flag on the button.
     * It runs after field and business-rule validation.
     *
     * The map key matches `actionKey` in `triggerAction`.
     * The value is a lambda that receives `formData` and should return `true` if validation passed.
     * On failure, the lambda is responsible for setting the errors in `errorManager`.
     *
     * @return Map of action-specific validations.
     */
    open fun defineActionValidations(): Map<String, (formResultData: FormResultData) -> Boolean> {
        return emptyMap()
    }
}