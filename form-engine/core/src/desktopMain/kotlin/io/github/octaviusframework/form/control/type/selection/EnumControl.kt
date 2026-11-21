package io.github.octaviusframework.form.control.type.selection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import org.octavius.domain.EnumWithFormatter
import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.control.type.selection.dropdown.DropdownControlBase
import io.github.octaviusframework.form.control.type.selection.dropdown.DropdownOption
import io.github.octaviusframework.form.localization.FormTr
import kotlin.reflect.KClass

/**
 * A control for choosing an enum value from a dropdown list.
 *
 * Generates the options from the enum's values. Requires the enum to implement EnumWithFormatter, which
 * formats the text shown to the user. Supports searching the options.
 */
class EnumControl<T>(
    label: String?,
    private val enumClass: KClass<T>,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<T>>? = null
) : DropdownControlBase<T>(
    label, required, dependencies, actions
) where T : Enum<T>, T : EnumWithFormatter<T> {

    override fun acceptsValue(value: Any) = enumClass.isInstance(value)

    // An enum carries its label with it, so "resolving" is immediate - suspend comes from the base
    // contract, where DatabaseControl has to query the database.
    override suspend fun resolveDisplayText(value: T): String = value.toDisplayString()

    @Composable
    override fun ColumnScope.RenderMenuItems(
        controlContext: ControlContext,
        scope: CoroutineScope,
        controlState: ControlState<T>,
        menuScrollState: ScrollState, // unused: an enum has all options at once, there is nothing to load
        closeMenu: () -> Unit
    ) {
        val options = enumClass.java.enumConstants.map {
            DropdownOption(it, it.toDisplayString())
        }

        // The "null" option
        if (required != true) {
            DropdownMenuItem(
                text = { Text(FormTr.Form.Dropdown.noSelection()) },
                onClick = {
                    controlState.value.value = null
                    controlState.displayText.value = null
                    executeActions(controlContext, null, scope)
                    closeMenu()
                }
            )
            HorizontalDivider()
        }

        // The options
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.displayText) },
                onClick = {
                    controlState.value.value = option.value
                    controlState.displayText.value = option.displayText
                    executeActions(controlContext, option.value, scope)
                    closeMenu()
                }
            )
        }
    }
}