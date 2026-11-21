package io.github.octaviusframework.form.control.type.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.validator.collection.StringListValidator
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.localization.Tr
import org.octavius.theme.FormSpacing

/**
 * A control for entering and managing a list of texts.
 *
 * Lets the user add, edit and delete text items. Every item can be edited on its own. New items are added
 * through a dedicated field, existing ones can be deleted.
 */
class StringListControl(
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: StringListValidation? = null
) : Control<List<String>>(
    label, required, dependencies, validationOptions = validationOptions, hasStandardLayout = false
) {
    override val validator: ControlValidator<List<String>> = StringListValidator(validationOptions)

    override fun acceptsValue(value: Any) = value is List<*> && value.all { it is String }

    override fun copyInitToValue(value: List<String>): List<String> {
        @Suppress("UNCHECKED_CAST") return value.map { it }.toList()
    }

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<List<String>>, isRequired: Boolean) {
        // Read straight from the model, without a local copy in remember, so that a write through
        // `updateControl` from an action shows up in the UI.
        val currentList = controlState.value.value ?: listOf()
        var newItemText by remember { mutableStateOf("") }

        val minItems = (validationOptions as? StringListValidation)?.minItems ?: 0
        val maxItems = (validationOptions as? StringListValidation)?.maxItems

        // Ensure minimum items
        LaunchedEffect(currentList.size) {
            if (currentList.size < minItems) {
                val itemsToAdd = minItems - currentList.size
                val updatedList = currentList.toMutableList()
                repeat(itemsToAdd) { updatedList.add("") }
                controlState.value.value = updatedList
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with label and add button
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = FormSpacing.labelPaddingStart,
                    bottom = FormSpacing.labelPaddingBottom
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Label with item count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label ?: "",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isRequired) {
                        Text(
                            text = "*",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = " (${currentList.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Add button
                val canAdd = maxItems == null || currentList.size < maxItems
                if (canAdd) {
                    IconButton(
                        onClick = {
                            val updatedList = currentList.toMutableList()
                            updatedList.add("")
                            controlState.value.value = updatedList
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = Tr.Action.add(),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FormSpacing.itemSpacing))

            // Items list - consistent spacing with RepeatableControl (8.dp between items)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FormSpacing.itemSpacing)
            ) {
                currentList.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small index indicator - consistent with label padding (4.dp start)
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp).padding(start = FormSpacing.fieldPaddingHorizontal)
                        )

                        OutlinedTextField(
                            value = item,
                            onValueChange = { newValue ->
                                val updatedList = currentList.toMutableList()
                                updatedList[index] = newValue
                                controlState.value.value = updatedList
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = FormSpacing.fieldPaddingHorizontal),
                            singleLine = true,
                            placeholder = { 
                                Text(
                                    text = FormTr.Form.StringList.itemPlaceholder(),
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            }
                        )

                        // Delete button with fixed width to maintain alignment
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val canDelete = currentList.size > minItems
                            if (canDelete) {
                                IconButton(
                                    onClick = {
                                        val updatedList = currentList.toMutableList()
                                        updatedList.removeAt(index)
                                        controlState.value.value = updatedList
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = Tr.Action.remove(),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            DisplayFieldErrors(controlContext)
        }
    }
}