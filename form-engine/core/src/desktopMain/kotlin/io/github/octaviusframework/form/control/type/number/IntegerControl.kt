package io.github.octaviusframework.form.control.type.number

import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.type.number.primitive.PrimitiveNumberControl

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