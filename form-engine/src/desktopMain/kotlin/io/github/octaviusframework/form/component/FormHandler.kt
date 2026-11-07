package io.github.octaviusframework.form.component

import androidx.compose.runtime.State
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.*
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.error.showUnhandledError
import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.ui.snackbar.SnackbarManager

/**
 * Handles the form's lifecycle - the main coordinator of the form system.
 *
 * FormHandler coordinates all form components:
 * - **FormSchema** - the structure and controls of the form
 * - **FormState** - reactive state of all controls
 * - **FormDataManager** - loading data from the database and processing results
 * - **FormValidator** - field and business-rule validation
 *
 * Lifecycle:
 * 1. Initialize the components and wire references between them
 * 2. Load data (editing) or set default values (new record)
 * 3. Handle user actions (validation + running the action)
 *
 * The handler knows nothing about navigation or about what happens after an action - it only returns
 * `true`/`false` from [triggerAction]. The decision "saving closes the screen" is made by the button's lambda
 * in the schema builder.
 *
 * @param formSchemaBuilder Builder providing the form's structure.
 * @param formDataManager Manager responsible for data operations.
 * @param formValidator Validator checking the data.
 * @param payload Additional data passed to the form (e.g. a parent ID).
 * @param handlerScope CoroutineScope for asynchronous operations.
 */
class FormHandler(
    formSchemaBuilder: FormSchemaBuilder,
    val formDataManager: FormDataManager,
    val formValidator: FormValidator = FormValidator(),
    private val payload: Map<String, Any?> = emptyMap(),
    private val handlerScope: CoroutineScope = CoroutineScope(SupervisorJob() + OctaviusDispatchers.Virtual)
) : FormActionTrigger {
    private val formState: FormState = FormState()
    // Field errors are keyed by the same full path as control states, including repeatable rows.
    internal val errorManager: ErrorManager = ErrorManager { path -> formState.getControlState(path) != null }
    private val formSchema: FormSchema = formSchemaBuilder.build()

    val loadState: State<FormLoadState> get() = formState.loadState
    val actionTriggered: State<Boolean> get() = formState.actionTriggered

    init {
        initializeControlsLifecycle()
        loadData()
    }

    /**
     * Sets the form component references on all controls.
     *
     * This is what gives controls access to the global state, the schema and the error manager.
     */
    private fun initializeControlsLifecycle() {
        formSchema.getAllControls().values.forEach { control ->
            control.initializeControlLifecycle(formState, formSchema, errorManager, this)
        }
        formValidator.setupFormReferences(formState, formSchema, errorManager)
        formDataManager.setupFormReferences(errorManager)
    }


    /**
     * API for FormView - access to the form components
     */
    internal fun getContentControlsInOrder(): List<String> = formSchema.contentOrder
    internal fun getActionBarControlsInOrder(): List<String> = formSchema.actionBarOrder
    internal fun getControl(name: String): Control<*>? = formSchema.getControl(name)
    internal fun getControlState(name: String): ControlState<*>? = formState.getControlState(name)

    /**
     * Asynchronously fetches the initial values from [FormDataManager.initData] (in `handlerScope`) and
     * creates the control states from them.
     *
     * Every failure ends in [FormLoadState.Failed]:
     * - `null` from `initData` is an expected failure (database) that the data manager has already shown;
     * - an exception is a bug in the form's code, e.g. [InitDataException] for data that does not fit the
     *   schema, so it goes to the unhandled-error dialog and to the log.
     */
    private fun loadData() {
        handlerScope.launch {
            formState.loadState.value = try {
                val initValues = formDataManager.initData(payload)
                if (initValues == null) {
                    FormLoadState.Failed
                } else {
                    formState.initializeStates(formSchema, initValues)
                    FormLoadState.Loaded
                }
            } catch (e: Exception) {
                showUnhandledError(e)
                FormLoadState.Failed
            }
        }
    }

    override suspend fun triggerAction(actionKey: String, validates: Boolean): Boolean {
        // Deliberately under handlerScope's job, not the caller's: the button launches the action in the
        // screen's scope, which dies with the screen, and a save made of several steps has to finish even
        // if the user closes the screen in the meantime. Hence also no cancelling of handlerScope on exit.
        return withContext(handlerScope.coroutineContext) {
            val formActions = formDataManager.definedFormActions()
            val action = formActions[actionKey] ?: run {
                GlobalDialogManager.show(ErrorDialogConfig("Exception", "No form action defined for key: $actionKey"))
                return@withContext false
            }

            formState.actionTriggered.value = true
            try {
                errorManager.clearAll()

                val formData = validatedFormData(actionKey, validates) ?: run {
                    SnackbarManager.showMessage(FormTr.Form.Actions.containsErrors())
                    return@withContext false
                }

                action.invoke(formData)
            } finally {
                // The overlay blocks the whole form, so it has to come off on every way out -
                // also when a validator or the action throws.
                formState.actionTriggered.value = false
            }
        }
    }

    /**
     * Collects the form data if it passes validation, or returns `null` when one of the stages failed it.
     * The stage itself records the errors to show, in `errorManager`.
     */
    private fun validatedFormData(actionKey: String, validates: Boolean): FormResultData? {
        if (validates && !formValidator.validateFields()) return null

        val formData = formState.collectFormData(formSchema)

        // Business-rule validation (may query the database)
        if (validates && !formValidator.validateBusinessRules(formData)) return null

        // Action-specific validation (always runs, regardless of the 'validates' flag)
        val actionValidator = formValidator.defineActionValidations()[actionKey]
        if (actionValidator != null && !actionValidator.invoke(formData)) return null

        return formData
    }
}