package io.github.octaviusframework.form.component

import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlContext

/**
 * Form schema - the structure and layout of the controls.
 *
 * FormSchema holds the complete form definition, including:
 * - All controls with their configuration
 * - The display order in the main area
 * - The display order in the action bar
 *
 * The schema is created by FormSchemaBuilder and does not change afterwards.
 *
 * @param controls Map of all form controls (key = unique name).
 * @param contentOrder Names of the controls shown in the main area of the form.
 * @param actionBarOrder Names of the controls shown in the bottom action bar.
 *
 * @throws IllegalArgumentException if names in the order lists do not exist in the controls map.
 */
class FormSchema(
    private val definedControls: Map<String, Control<*>>,
    val contentOrder: List<String>,
    val actionBarOrder: List<String>
) {

    val controls: Map<String, Control<*>>

    init {
        val ctrls = definedControls.toMutableMap()
        definedControls.forEach { (key, value) ->
            ctrls.putAll(value.registerChildrenInGlobalMap(ControlContext(key)))
        }
        controls = ctrls.toMap()
    }

    /**
     * Returns the control with the given name
     */
    fun getControl(name: String): Control<*>? = controls[name]

    /**
     * Returns all controls defined in this schema
     */
    fun getAllControls(): Map<String, Control<*>> = controls

    /**
     * Top-level controls of the definition, with a context without a parent. The engine walks down the tree
     * from them through [Control.childContexts].
     */
    internal fun rootContexts(): List<Pair<ControlContext, Control<*>>> {
        return definedControls.map { (name, control) -> ControlContext(name) to control }
    }
}

/**
 * Abstract factory for form schemas.
 *
 * Every form has its own builder implementation, which defines the structure of its controls, their order
 * and their configuration for the given business domain.
 *
 * Example implementation:
 * ```kotlin
 * class UserFormSchemaBuilder : FormSchemaBuilder() {
 *     override fun build(): FormSchema {
 *         return FormSchema(
 *             controls = mapOf(
 *                 "name" to StringControl(label = "Name", required = true),
 *                 "email" to StringControl(label = "Email")
 *             ),
 *             contentOrder = listOf("name", "email"),
 *             actionBarOrder = listOf("save", "cancel")
 *         )
 *     }
 * }
 * ```
 */
abstract class FormSchemaBuilder {
    fun build(): FormSchema {
        return FormSchema(
            defineControls(),
            defineContentOrder(),
            defineActionBarOrder()
        )
    }

    abstract fun defineControls() : Map<String, Control<*>>

    abstract fun defineContentOrder() : List<String>

    abstract fun defineActionBarOrder() : List<String>

}