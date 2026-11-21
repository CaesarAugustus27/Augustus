package io.github.octaviusframework.form.component

import io.github.octaviusframework.form.control.base.FormResultData

/**
 * Abstract class managing the data flow of a form.
 *
 * FormDataManager is responsible for:
 * - Providing the initial values: loaded for an edited record, defaults for a new one
 * - Turning form data into the effects of its actions (saving, deleting)
 *
 * Every form implements its own DataManager. The engine knows nothing about where the data comes from or
 * goes to.
 */
abstract class FormDataManager {

    lateinit var errorManager: ErrorManager
    internal fun setupFormReferences(errorManager: ErrorManager) {
        this.errorManager = errorManager
    }

    /**
     * Provides the initial values for the form's controls.
     *
     * Every key must point at a control, and every value must fit that control's type - otherwise the
     * engine throws `InitDataException` with that path before the form is drawn.
     *
     * @param payload additional data for the form (an empty map when there is none)
     * @return map control -> value with default or computed values. `null` means loading failed and the
     *   method has already shown why - the form is then left without controls. Like `false` from an action,
     *   `null` carries the fact of failure, not its content.
     */
    abstract fun initData(payload: Map<String, Any?>): Map<String, Any?>?

    /**
    * Defines the logic of form actions that operate on data (Save, Delete, etc.).
    * The map key matches `actionKey` in `FormActionTrigger.triggerAction`.
    * The value is a lambda that receives the current form data and returns `true` if the operation
    * succeeded.
    *
    * The lambda shows the failures it expects in `errorManager` and returns `false`, which carries the fact
    * of failure, not its content. Any other exception it lets through to the global error dialog.
    *
    * Actions that do not touch data do not belong here. Closing the screen is the business of the button in
    * the schema builder, not of the data layer.
    *
    * @return Map of form actions.
    */
    open fun definedFormActions(): Map<String, (formResultData: FormResultData) -> Boolean> {
        return emptyMap()
    }
}
