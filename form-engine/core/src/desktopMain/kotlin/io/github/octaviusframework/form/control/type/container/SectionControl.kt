package io.github.octaviusframework.form.control.type.container

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.octaviusframework.form.component.InitDataException
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.section.SectionContent
import io.github.octaviusframework.form.control.layout.section.SectionHeader
import org.octavius.theme.FormSpacing

/**
 * A control grouping other controls into sections.
 *
 * Renders a group of controls in a card with a header. Supports collapsing/expanding the section and laying
 * out the controls in columns. Organizes the form into thematic sections.
 *
 * A section is one data level, like a repeatable row: its children lie under `section/name`, and the
 * section's result carries the map of their results. The children's visibility depends on the section's.
 *
 * @param controls The section's controls, in display order.
 */
class SectionControl(
    val controls: Map<String, Control<*>>,
    val collapsible: Boolean = true,
    val initiallyExpanded: Boolean = true,
    val columns: Int = 1,
    label: String,
    dependencies: Map<String, ControlDependency<*>>? = null
) : Control<Unit>(label, false, dependencies, hasStandardLayout = false) {

    override fun registerChildrenInGlobalMap(controlContext: ControlContext): Map<String, Control<*>> {
        val map = mutableMapOf<String, Control<*>>()
        childContexts(controlContext).forEach { (childControlContext, child) ->
            map[childControlContext.fullControlPath] = child
            map.putAll(child.registerChildrenInGlobalMap(childControlContext))
        }
        return map
    }

    override fun childContexts(controlContext: ControlContext): List<Pair<ControlContext, Control<*>>> {
        return controls.map { (childName, child) -> controlContext.forSectionChild(childName) to child }
    }

    override fun acceptsValue(value: Any) = false

    // A section's initial value is the map of its fields, which FormState assembled from `section/field` paths.
    override fun setInitValue(controlContext: ControlContext, value: Any?): ControlState<Unit> {
        if (value != null && value !is Map<*, *>) throw InitDataException(controlContext.fullControlPath, mismatch(value))
        @Suppress("UNCHECKED_CAST")
        formState.initializeLevel(controlContext.fullControlPath, childContexts(controlContext), (value as Map<String, Any?>?).orEmpty())
        return ControlState()
    }

    // A section has no value of its own - it carries its children's results, in both fields, because every
    // child has both a current and an initial value. A hidden section loses its current map, but its children
    // return `null` anyway.
    override fun convertToResult(controlContext: ControlContext, state: ControlState<*>): ControlResultData {
        val children = formState.collectLevel(childContexts(controlContext))
        return ControlResultData(currentValue = children, initialValue = children)
    }

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<Unit>, isRequired: Boolean) {
        val isExpanded = remember { mutableStateOf(initiallyExpanded) }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FormSpacing.containerPaddingVertical),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    label = label,
                    collapsible = collapsible,
                    isExpanded = isExpanded.value,
                    onToggle = { isExpanded.value = !isExpanded.value }
                )

                AnimatedVisibility(visible = isExpanded.value) {
                    SectionContent(
                        children = childContexts(controlContext),
                        columns = columns,
                        formState = this@SectionControl.formState
                    )
                }
            }
        }
    }
}
