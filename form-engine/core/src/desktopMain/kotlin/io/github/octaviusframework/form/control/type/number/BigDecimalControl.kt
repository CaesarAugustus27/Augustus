package io.github.octaviusframework.form.control.type.number

import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.type.number.primitive.PrimitiveNumberControl
import java.math.BigDecimal

/**
 * A control for entering high-precision numbers (BigDecimal).
 * Extends PrimitiveNumberControl with parsing for BigDecimal.
 */
class BigDecimalControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    private val validationOptions: BigDecimalValidation? = null,
    actions: List<ControlAction<BigDecimal>>? = null
) : PrimitiveNumberControl<BigDecimal>(
    label,
    required,
    dependencies,
    actions = actions
) {
    override fun validationErrors(controlContext: ControlContext, value: BigDecimal) = validationOptions?.errors(value).orEmpty()

    override fun acceptsValue(value: Any) = value is BigDecimal

    override fun parseValue(text: String): BigDecimal? {
        // Both a dot and a comma work as the decimal separator
        val cleanText = text.replace(",", ".")
        return cleanText.toBigDecimalOrNull()
    }
}