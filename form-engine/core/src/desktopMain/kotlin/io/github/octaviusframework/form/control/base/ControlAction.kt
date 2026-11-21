package io.github.octaviusframework.form.control.base

import kotlinx.coroutines.CoroutineScope
import io.github.octaviusframework.form.component.ErrorManager
import io.github.octaviusframework.form.component.FormActionTrigger
import io.github.octaviusframework.form.component.FormSchema
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.component.PathResolver

private val ROW_ID = Regex("""\[[^\]]*]""")

/**
 * Defines an action to run after a control's value changes.
 *
 * @param T type of the value of the control that triggers the action.
 * @param action Lambda to run. Gets ActionContext as its receiver (`this`).
 */
class ControlAction<T>(
    val executeOnInit: Boolean = false,
    val action: suspend ActionContext<T>.() -> Unit
)

/**
 * Context given to a ControlAction, with access to the form state and tools.
 *
 * @param T type of the value of the control that triggered the action.
 * @property sourceValue New value of the control that triggered the action.
 * @property sourceControlContext Context of the control that triggered the action.
 * @property formState Access to the global form state.
 * @property formSchema Access to the form schema.
 * @property errorManager Access to the error manager.
 * @property coroutineScope Scope for launching asynchronous operations (e.g. API calls).
 */
data class ActionContext<T>(
    val sourceValue: T?,
    val sourceControlContext: ControlContext,
    val formState: FormState,
    val formSchema: FormSchema,
    val errorManager: ErrorManager,
    val trigger: FormActionTrigger,
    val coroutineScope: CoroutineScope,
    val payload: Any? = null // Additional data, e.g. the extra value of a dropdown control
) {
    /**
     * Reads the current value of the control at a relative (./, ../) or absolute path.
     *
     * The reading half of the pair with [updateControl]. Needed wherever an action does not replace the
     * value but adds to it - for example appends titles from another source to a list instead of wiping
     * what is already there.
     *
     * Returns `null` when the control has no value. A path without a control throws.
     */
    fun <V : Any> readControl(controlPath: String): V? {
        // Unchecked cast - V is erased, the caller is responsible for the type.
        @Suppress("UNCHECKED_CAST")
        return stateAt(resolvedPath(controlPath)).value.value as V?
    }

    /**
     * Updates the value of a control at a relative (./, ../) or absolute path.
     * A value the control does not accept (`Control.acceptsValue`) throws.
     */
    fun <V: Any> updateControl(controlPath: String, newValue: V?) {
        setValue(controlPath, resolvedPath(controlPath), newValue)
    }

    /**
     * Updates the values of all controls matching a pattern (e.g. with a * wildcard).
     * The pattern may match nothing - a list of rows can be empty. A path without a wildcard works like
     * [updateControl], so without a control it throws.
     */
    fun <V: Any> updateControls(controlPath: String, newValue: V?) {
        if ("*" !in controlPath) return updateControl(controlPath, newValue)

        PathResolver.resolvePaths(controlPath, sourceControlContext, formState).forEach { resolvedName ->
            setValue(controlPath, resolvedName, newValue)
        }
    }

    /**
     * Updates the control's label (overrides the default one).
     */
    fun updateLabel(controlPath: String, newLabel: String?) {
        stateAt(resolvedPath(controlPath)).labelOverride.value = newLabel
    }

    // A wrong path is a bug in the schema - an action silently skipped would hide it forever.
    private fun resolvedPath(controlPath: String): String {
        val resolvedName = PathResolver.resolvePath(controlPath, sourceControlContext)
        require(formState.getControlState(resolvedName) != null) {
            "Action of control '${sourceControlContext.fullStatePath}' points at '$controlPath' " +
                "(resolved to '$resolvedName'), but the form has no such control"
        }
        return resolvedName
    }

    private fun stateAt(statePath: String): ControlState<*> = formState.getControlState(statePath)!!

    // State paths of repeatable rows carry `[rowId]`, the schema knows their controls without it.
    private fun controlAt(statePath: String): Control<*> {
        val controlPath = statePath.replace(ROW_ID, "")
        return checkNotNull(formSchema.getControl(controlPath)) { "No control at '$controlPath' for state '$statePath'" }
    }

    private fun <V : Any> setValue(controlPath: String, statePath: String, newValue: V?) {
        if (newValue != null) {
            controlAt(statePath).rejectionOf(newValue)?.let { reason ->
                throw IllegalArgumentException(
                    "Action of control '${sourceControlContext.fullStatePath}' sets '$controlPath' " +
                        "(resolved to '$statePath'): $reason"
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        val typedState = stateAt(statePath) as ControlState<V>
        typedState.value.value = newValue
        // The text described the old value, so it no longer holds. The control works it out again -
        // a number right away, a dropdown with a query.
        typedState.displayText.value = null
    }
}