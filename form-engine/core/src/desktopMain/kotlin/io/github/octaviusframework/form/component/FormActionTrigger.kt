package io.github.octaviusframework.form.component

/**
 * The contract through which controls run form actions.
 *
 * `triggerAction` returns only whether the action succeeded - and that is all the information the engine
 * hands out. What happens next (closing the screen, going somewhere else) is decided by the button's lambda
 * in the schema builder, i.e. feature code, which knows the navigation anyway.
 *
 */
interface FormActionTrigger {
    /**
     * Runs the whole action: validation, collecting the form data, executing the action.
     *
     * @param actionKey Key from `FormDataManager.definedFormActions()`.
     * @param validates Whether to run field and business-rule validation before the action.
     * @return `true` when the action ran and reported success; `false` when validation failed, there is no
     *   action with that key, or the action itself reported failure.
     */
    suspend fun triggerAction(actionKey: String, validates: Boolean): Boolean
}
