package io.github.octaviusframework.form.control.type.primitive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.validator.primitive.StringValidator
import org.octavius.theme.FormSpacing
import java.awt.Cursor

/**
 * A control for entering long (multiline) text.
 *
 * Has a built-in handle (bottom right corner) that lets the user change the field's height (like a textarea
 * in HTML).
 */
class MultilineStringControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: StringValidation? = null,
    actions: List<ControlAction<String>>? = null,
    private val minHeightDp: Int = 120
) : Control<String>(
    label,
    required,
    dependencies,
    validationOptions = validationOptions,
    actions = actions
) {
    override val validator: ControlValidator<String> = StringValidator(validationOptions)

    override fun acceptsValue(value: Any) = value is String

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<String>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()
        var height by remember { mutableStateOf(minHeightDp.dp) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = FormSpacing.fieldPaddingVertical,
                    horizontal = FormSpacing.fieldPaddingHorizontal
                )
        ) {
            OutlinedTextField(
                value = controlState.value.value.orEmpty(),
                onValueChange = {
                    controlState.value.value = it
                    executeActions(controlContext, it, scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                singleLine = false
            )

            // Resize handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Small offset so it does not sit right on the border
                    .padding(bottom = 6.dp, end = 6.dp)
                    .size(16.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            with(density) {
                                val newHeight = height + dragAmount.y.toDp()
                                height = maxOf(minHeightDp.dp, newHeight)
                            }
                        }
                    }
            ) {
                val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Draw the handle's stripes in the bottom right corner
                    drawLine(color, Offset(w, h * 0.4f), Offset(w * 0.4f, h), strokeWidth = 2f)
                    drawLine(color, Offset(w, h * 0.7f), Offset(w * 0.7f, h), strokeWidth = 2f)
                    drawLine(color, Offset(w, h * 1.0f), Offset(w * 1.0f, h), strokeWidth = 2f)
                }
            }
        }
    }
}
