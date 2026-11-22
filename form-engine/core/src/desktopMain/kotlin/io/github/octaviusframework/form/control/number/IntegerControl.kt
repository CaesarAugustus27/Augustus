package io.github.octaviusframework.form.control.number

import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.ControlContext
import io.github.octaviusframework.form.control.ControlDependency

/**
 * A control for entering integers.
 * Extends PrimitiveNumberControl with parsing for Int.
 */
class IntegerControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    private val validationOptions: IntegerValidation? = null,
    actions: List<ControlAction<Int>>? = null
) : PrimitiveNumberControl<Int>(
    label,
    required,
    dependencies,
    actions = actions
) {
    override fun validationErrors(controlContext: ControlContext, value: Int) = validationOptions?.errors(value).orEmpty()

    override fun acceptsValue(value: Any) = value is Int

    override fun parseValue(text: String): Int? {
        return text.toIntOrNull()
    }
}