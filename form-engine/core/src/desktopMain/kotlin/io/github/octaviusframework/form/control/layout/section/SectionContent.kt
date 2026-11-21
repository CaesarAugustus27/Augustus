package io.github.octaviusframework.form.control.layout.section

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlContext
import org.octavius.theme.FormSpacing

@Composable
internal fun SectionContent(
    children: List<Pair<ControlContext, Control<*>>>,
    columns: Int,
    formState: FormState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FormSpacing.sectionPadding)
        ) {
            if (columns > 1) {
                // Several columns
                RenderMultiColumnContent(children, columns, formState)
            } else {
                // A single column
                RenderSingleColumnContent(children, formState)
            }
        }
    }
}

@Composable
private fun RenderMultiColumnContent(
    children: List<Pair<ControlContext, Control<*>>>,
    columns: Int,
    formState: FormState
) {
    // Split the controls into a group per column
    val childGroups = children.chunked(
        (children.size + columns - 1) / columns
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        childGroups.forEach { group ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FormSpacing.fieldPaddingHorizontal)
            ) {
                group.forEach { (childContext, child) ->
                    RenderChild(childContext, child, formState)
                    Spacer(modifier = Modifier.height(FormSpacing.sectionContentSpacing))
                }
            }
        }
    }
}

@Composable
private fun RenderSingleColumnContent(
    children: List<Pair<ControlContext, Control<*>>>,
    formState: FormState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        children.forEach { (childContext, child) ->
            RenderChild(childContext, child, formState)
            Spacer(modifier = Modifier.height(FormSpacing.sectionHeaderPaddingBottom))
        }
    }
}

// Rendering of a single control, shared by both layouts
@Composable
private fun RenderChild(
    childContext: ControlContext,
    child: Control<*>,
    formState: FormState
) {
    formState.getControlState(childContext.fullStatePath)?.let { state ->
        child.Render(childContext, state)
    }
}
