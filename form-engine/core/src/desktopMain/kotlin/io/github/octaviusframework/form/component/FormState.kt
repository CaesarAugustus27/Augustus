package io.github.octaviusframework.form.component

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.octaviusframework.form.control.base.*

/**
 * Reactive state of all form controls.
 *
 * FormState holds and manages the states of all controls in the form, using the Compose State API so the UI
 * recomposes automatically on changes.
 *
 * Main functions:
 * - Holding reactive control states (MutableState)
 * - Triggering recomposition on changes
 * - Managing hierarchical control names (e.g. for RepeatableControl)
 * - Collecting data from all controls for processing
 *
 * Control states are keyed by full path (e.g. "publications[uuid].title"), which is what supports complex,
 * nested structures.
 */
class FormState {

    /**
     * Stage of loading the initial data (initData). Until it is [FormLoadState.Loaded] the controls have no
     * states, so there is nothing to draw.
     */
    val loadState = mutableStateOf(FormLoadState.Loading)

    /**
     * Form submission flag (triggerAction).
     * True while an action (e.g. save, delete) is running.
     */
    val actionTriggered = mutableStateOf(false)

    /**
     * Control states - a reactive map that triggers recomposition automatically
     */
    private val _controlStates = mutableStateMapOf<String, ControlState<*>>()

    /**
     * Returns the state of the control with the given name
     */
    fun getControlState(name: String): ControlState<*>? = _controlStates[name]

    /**
     * Returns the states of all form controls
     */
    fun getAllStates(): Map<String, ControlState<*>> = _controlStates

    /**
     * Initializes the states of all controls from the initial values.
     *
     * @param schema Form schema with the control definitions.
     * @param initValues Map of initial values (key = control name).
     * @throws InitDataException when the values do not fit the schema.
     */
    internal fun initializeStates(schema: FormSchema, initValues: Map<String, Any?>) {
        initializeLevel("", schema.rootContexts(), initValues)
    }

    /**
     * Creates the control states of one data level - the form, a section or a repeatable row - from the
     * values under their names. Containers set up their own levels in `setInitValue`, with this same function.
     *
     * Section fields come under paths (`"basic_info/name"`), because that is how `loadData` returns them,
     * and only then does merging defaults, database values and the payload with a plain `+` keep all fields.
     *
     * Every value has to hit a control of this level.
     *
     * @param levelPath Path of the level in the schema (`""` for the root, `"basic_info"`, `"publications"`),
     *   for error messages.
     * @throws InitDataException when a value hits no control or the control does not accept it.
     */
    internal fun initializeLevel(levelPath: String, controls: List<Pair<ControlContext, Control<*>>>, values: Map<String, Any?>) {
        val valuesByName = valuesByName(levelPath, values)
        val controlNames = controls.map { (controlContext, _) -> controlContext.localName }.toSet()
        valuesByName.keys.firstOrNull { it !in controlNames }?.let { name ->
            throw InitDataException(pathIn(levelPath, name), "no such control in the form")
        }
        controls.forEach { (controlContext, control) ->
            _controlStates[controlContext.fullStatePath] = control.setInitValue(controlContext, valuesByName[controlContext.localName])
        }
    }

    // Groups paths by their first segment: "basic_info/name" goes into the "basic_info" map as "name".
    // Deeper segments are split by the next level, when the section hands it its map.
    private fun valuesByName(levelPath: String, values: Map<String, Any?>): Map<String, Any?> {
        return values.entries.groupBy { it.key.substringBefore(PathResolver.SEPARATOR) }.mapValues { (name, entries) ->
            val path = pathIn(levelPath, name)
            val ownValue = entries.singleOrNull { it.key == name }
            // No control holds a map, so a map under a bare name is a section given as a whole.
            // `+` on such maps replaces the whole section, i.e. silently drops fields - hence paths only.
            if (ownValue?.value is Map<*, *>) {
                throw InitDataException(path, "value is a map - section fields are given as paths, e.g. '$path/field'")
            }
            when {
                ownValue == null -> entries.associate { it.key.substringAfter(PathResolver.SEPARATOR) to it.value }
                entries.size == 1 -> ownValue.value
                else -> throw InitDataException(path, "value given both at '$path' and at paths under '$path/...'")
            }
        }
    }

    private fun pathIn(levelPath: String, name: String): String {
        return if (levelPath.isEmpty()) name else "$levelPath${PathResolver.SEPARATOR}$name"
    }

    /**
     * Sets the state of the control with the given name (may be hierarchical)
     * Triggers recomposition automatically thanks to mutableStateMapOf
     */
    internal fun setControlState(name: String, state: ControlState<*>) {
        _controlStates[name] = state
    }

    /**
     * Removes the state of the control with the given name
     * Triggers recomposition automatically thanks to mutableStateMapOf
     */
    internal fun removeControlState(name: String) {
        _controlStates.remove(name)
    }

    /**
     * Removes all control states starting with the given prefix
     * Triggers recomposition automatically thanks to mutableStateMapOf
     */
    internal fun removeControlStatesWithPrefix(prefix: String) {
        val keysToRemove = _controlStates.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { _controlStates.remove(it) }
    }

    /**
     * Collects and processes the data of all form controls.
     *
     * @param schema Form schema with the control definitions.
     * @return FormResultData - map of results (key = control name, value = ControlResultData).
     */
    internal fun collectFormData(schema: FormSchema): FormResultData {
        return collectLevel(schema.rootContexts())
    }

    /**
     * Collects the results of the controls of one data level under their names. Containers collect their own
     * levels, with this same function: a section once, a repeatable per row. Every control gets a context with
     * its parent, because only that tells whether it is visible - and an invisible one passes no value to save.
     */
    internal fun collectLevel(controls: List<Pair<ControlContext, Control<*>>>): FormResultData {
        return controls.associate { (controlContext, control) ->
            controlContext.localName to control.getResult(controlContext, _controlStates.getValue(controlContext.fullStatePath))
        }
    }
}

/**
 * Stage of loading the form's initial data.
 */
enum class FormLoadState {
    /** `initData` is still running. */
    Loading,

    /** Control states are ready, the form can be drawn. */
    Loaded,

    /**
     * Loading failed and a dialog has already shown why. Control states are missing or incomplete, so the
     * form can be neither drawn nor saved.
     */
    Failed
}