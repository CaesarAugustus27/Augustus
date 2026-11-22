package io.github.octaviusframework.form.control.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.component.ErrorManager
import io.github.octaviusframework.form.component.FormActionTrigger
import io.github.octaviusframework.form.component.FormSchema
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.component.InitDataException
import io.github.octaviusframework.form.component.PathResolver
import io.github.octaviusframework.form.control.layout.RenderFieldError
import io.github.octaviusframework.form.control.layout.RenderNormalLabel
import io.github.octaviusframework.form.localization.FormTr

/**
 * Abstract base class of all form controls.
 *
 * @param T type of the data held by the control
 */
abstract class Control<T : Any> internal constructor(
    // --- 1. Configuration and properties ---
    internal val label: String?,
    internal val required: Boolean?,
    internal val dependencies: Map<String, ControlDependency<*>>?,
    private val actions: List<ControlAction<T>>? = null,
    protected val hasStandardLayout: Boolean = true
) {

    // --- 2. Hierarchy ---

    /**
     * Registers the children in the global map
     * Holds references to the controls defined inside a control that has its own
     */
    internal open fun registerChildrenInGlobalMap(controlContext: ControlContext): Map<String, Control<*>> {
        return emptyMap()
    }

    /**
     * Children of this control in the current form state, each with a context in which this control is the
     * parent: a section has one set, a repeatable - one per row. The engine walks down the tree through them
     * when initializing, validating and collecting data.
     */
    internal open fun childContexts(controlContext: ControlContext): List<Pair<ControlContext, Control<*>>> {
        return emptyList()
    }


    // --- 3. Lifecycle and form context ---
    // References injected by FormHandler at initialization.
    protected lateinit var formState: FormState
    protected lateinit var formSchema: FormSchema
    protected lateinit var errorManager: ErrorManager
    protected lateinit var formActionTrigger: FormActionTrigger

    /**
     * Sets the references to the form components. This is the second stage of a control's initialization,
     * called by FormHandler. Gives access to the global state, the schema and the error manager.
     * Also lets particular controls run additional initialization steps they need
     */
    internal open fun initializeControlLifecycle(
        formState: FormState,
        formSchema: FormSchema,
        errorManager: ErrorManager,
        formActionTrigger: FormActionTrigger
    ) {
        this.formState = formState
        this.formSchema = formSchema
        this.errorManager = errorManager
        this.formActionTrigger = formActionTrigger
    }

    // --- 4. State and data ---
    /**
     * Sets the control's initial value and creates its state (`ControlState`).
     * @param value Value from the database or a default value.
     * @return The created control state.
     * @throws InitDataException when the control does not accept [value].
     */
    internal open fun setInitValue(controlContext: ControlContext, value: Any?): ControlState<T> {
        val state = ControlState<T>()
        if (value == null) {
            return state
        }

        rejectionOf(value)?.let { reason -> throw InitDataException(controlContext.fullControlPath, reason) }
        @Suppress("UNCHECKED_CAST")
        state.initValue.value = value as T
        state.value.value = copyInitToValue(value)
        return state
    }

    /**
     * Whether this control can hold [value], i.e. whether it is a value of type [T] (and for a choice from fixed
     * options - one of them). Checked for initial values and in `updateControl`. Controls without a value of
     * their own accept nothing: a section, a button, and a repeatable list, which manages its rows itself.
     * Containers check the shape of their initial data in `setInitValue`.
     */
    protected abstract fun acceptsValue(value: Any): Boolean

    /**
     * Why the control does not accept [value], or `null` when it does.
     */
    internal fun rejectionOf(value: Any): String? = if (acceptsValue(value)) null else mismatch(value)

    /**
     * Describes [value] as not fitting this control, for exception messages.
     */
    protected fun mismatch(value: Any): String {
        return "value ${describeValue(value)} does not fit ${this::class.simpleName}"
    }

    /**
     * Makes a deep copy of the initial value for the current value.
     * Matters for mutable types (e.g. List), so that changes to `value` do not affect `initValue`.
     */
    internal open fun copyInitToValue(value: T): T {
        return value // Default implementation for immutable types.
    }

    /**
     * Returns the control's result (current and initial value) for further processing.
     * If the control is invisible, its `currentValue` is set to null.
     */
    internal fun getResult(controlContext: ControlContext, state: ControlState<*>): ControlResultData {
        val result = convertToResult(controlContext, state)
        // Invisible controls do not pass their value to save.
        return if (!isVisible(controlContext)) {
            result.copy(currentValue = null)
        } else {
            result
        }
    }

    /**
     * Converts the control's internal state (`ControlState`) into a result to save (`ControlResultData`).
     * Can be overridden for custom conversions (e.g. in `RepeatableControl`).
     */
    protected open fun convertToResult(controlContext: ControlContext, state: ControlState<*>): ControlResultData {
        return ControlResultData(currentValue = state.value.value, initialValue = state.initValue.value)
    }

    // --- 5. Visibility, requirement and validation ---
    /**
     * Whether the control shows: its parent shows and every `Visible` dependency is met.
     */
    internal fun isVisible(controlContext: ControlContext): Boolean {
        controlContext.parent?.let { parentContext ->
            if (!formSchema.getControl(parentContext.fullControlPath)!!.isVisible(parentContext)) return false
        }
        return dependenciesOf(DependencyType.Visible).all { isDependencyMet(it, controlContext) }
    }

    /**
     * Whether the control must be filled in: `required = true` or any `Required` dependency is met.
     */
    internal fun isRequired(controlContext: ControlContext): Boolean {
        if (required == true) return true
        return dependenciesOf(DependencyType.Required).any { isDependencyMet(it, controlContext) }
    }

    private fun dependenciesOf(type: DependencyType): List<ControlDependency<*>> =
        dependencies.orEmpty().values.filter { it.dependencyType == type }

    private fun isDependencyMet(dependency: ControlDependency<*>, controlContext: ControlContext): Boolean {
        val resolvedPath = PathResolver.resolvePath(dependency.controlPath, controlContext)
        // A wrong path is a bug in the schema - a dependency silently met would hide it forever.
        val dependentState = formState.getControlState(resolvedPath) ?: throw IllegalArgumentException(
            "Dependency of control '${controlContext.fullStatePath}' points at '${dependency.controlPath}' " +
                "(resolved to '$resolvedPath'), but the form has no such control"
        )
        val dependentValue = dependentState.value.value

        return when (dependency.comparisonType) {
            ComparisonType.OneOf -> {
                val acceptedValues = dependency.value as? List<*> ?: listOf(dependency.value)
                dependentValue in acceptedValues
            }
            ComparisonType.Equals -> dependentValue == dependency.value
            ComparisonType.NotEquals -> dependentValue != dependency.value
        }
    }

    /**
     * Validates the control and records its field errors. A hidden control is skipped, a required one must not
     * be empty (`null`, a blank string, an empty list), and any other non-null value goes through
     * [validationErrors].
     */
    internal fun validateControl(controlContext: ControlContext, state: ControlState<*>) {
        if (!isVisible(controlContext)) return

        val value = state.value.value
        if (isRequired(controlContext) && isEmpty(value)) {
            errorManager.setFieldErrors(controlContext.fullStatePath, listOf(FormTr.Validation.fieldRequired()))
            return
        }

        // acceptsValue keeps any value of the state a T.
        @Suppress("UNCHECKED_CAST")
        val typedValue = value as T? ?: return
        errorManager.setFieldErrors(controlContext.fullStatePath, validationErrors(controlContext, typedValue))
    }

    private fun isEmpty(value: Any?): Boolean = when (value) {
        null -> true
        is String -> value.isBlank()
        is List<*> -> value.isEmpty()
        else -> false
    }

    /**
     * Messages for a non-null value that breaks the rules of this control type, e.g. its `validationOptions`.
     */
    protected open fun validationErrors(controlContext: ControlContext, value: T): List<String> = emptyList()

    // --- 6. Actions ---
    /**
     * Runs the actions defined for this control, usually after a value change.
     */
    protected fun executeActions(
        controlContext: ControlContext,
        newValue: T?,
        scope: CoroutineScope,
        payload: Any? = null
    ) {
        actions?.forEach { action ->
            scope.launch {
                val context = ActionContext(
                    sourceValue = newValue,
                    sourceControlContext = controlContext,
                    formState = formState,
                    formSchema = formSchema,
                    errorManager = errorManager,
                    trigger = formActionTrigger,
                    coroutineScope = scope,
                    payload = payload
                )
                action.action.invoke(context)
            }
        }
    }

    internal fun executeInitActions(
        controlContext: ControlContext,
        currentValue: T?,
        scope: CoroutineScope
    ) {
        actions?.filter { it.executeOnInit }?.forEach { action ->
            scope.launch {
                val context = ActionContext(
                    sourceValue = currentValue,
                    sourceControlContext = controlContext,
                    formState = formState,
                    formSchema = formSchema,
                    errorManager = errorManager,
                    trigger = formActionTrigger,
                    coroutineScope = scope
                )
                action.action.invoke(context)
            }
        }
    }

    // --- 7. UI rendering ---
    /**
     * Renders the control in the user interface.
     * This is the entry point for `FormView`. Handles visibility.
     */
    @Composable
    internal fun Render(controlContext: ControlContext, controlState: ControlState<*>) {
        val isVisible = isVisible(controlContext)
        val isRequired = isRequired(controlContext)
        val scope = rememberSupervisedCoroutineScope()

        @Suppress("UNCHECKED_CAST")
        val typedState = controlState as ControlState<T>

        androidx.compose.runtime.LaunchedEffect(Unit) {
            executeInitActions(controlContext, typedState.value.value, scope)
        }

        AnimatedVisibility(visible = isVisible) {
            if (hasStandardLayout) {
                // Standard layout: label above the control, errors below.
                Column {
                    RenderNormalLabel(controlState.labelOverride.value ?: label, isRequired)
                    Display(controlContext, typedState, isRequired)
                    DisplayFieldErrors(controlContext)
                }
            } else {
                // Custom layout: the control manages its own layout.
                Display(controlContext, typedState, isRequired)
            }
        }
    }

    /**
     * Renders the control's own UI.
     * Every concrete control type (e.g. `StringControl`) implements it.
     */
    @Composable
    protected abstract fun Display(
        controlContext: ControlContext,
        controlState: ControlState<T>,
        isRequired: Boolean
    )

    /**
     * Renders this control's validation errors.
     */
    @Composable
    protected fun DisplayFieldErrors(controlContext: ControlContext) {
        val formatError = errorManager.getFormatError(controlContext.fullStatePath)
        formatError?.let { error -> RenderFieldError(error) }

        val fieldErrors = errorManager.getFieldErrors(controlContext.fullStatePath)
        fieldErrors.forEach { error ->
            RenderFieldError(error)
        }
    }
}