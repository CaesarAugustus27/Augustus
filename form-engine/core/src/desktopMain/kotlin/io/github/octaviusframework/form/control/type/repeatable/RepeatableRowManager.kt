package io.github.octaviusframework.form.control.type.repeatable

import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.control.base.RepeatableValidation

class RepeatableRowManager(
    private val rowControls: Map<String, Control<*>>,
    private val formState: FormState,
    private val validationOptions: RepeatableValidation?
) {
    
    fun addRow(controlContext: ControlContext, controlState: ControlState<List<RepeatableRow>>) {
        val currentRows = controlState.value.value!!.toMutableList()
        val newIndex = currentRows.size
        val newRow = createRow(newIndex, controlContext, rowControls, formState)
        currentRows.add(newRow)
        controlState.value.value = currentRows
    }
    
    fun deleteRow(
        controlContext: ControlContext,
        controlState: ControlState<List<RepeatableRow>>,
        rowId: String
    ) {
        val minRows = validationOptions?.minItems ?: 0
        if (controlState.value.value!!.size <= minRows) {
            return
        }

        val currentRows = controlState.value.value!!.toMutableList()
        val index = currentRows.indexOfFirst { it.id == rowId }
        if (index < 0) return
        val rowToRemove = currentRows[index]
        
        // Was the row in the original data
        val wasOriginal = controlState.initValue.value!!.any { it.id == rowToRemove.id }
        
        // The states of a database row stay for the lifetime of the form: its initial values go to
        // deletedRows on every data collection. A new row has nothing to report.
        if (!wasOriginal) {
            formState.removeControlStatesWithPrefix("${controlContext.fullStatePath}[${rowToRemove.id}]")
        }
        
        // Remove the row from the list
        currentRows.removeAt(index)
        
        // Update the indexes
        currentRows.forEachIndexed { newIndex, row ->
            currentRows[newIndex] = row.copy(index = newIndex)
        }
        
        controlState.value.value = currentRows
        return
    }
    
    fun canAddRow(controlState: ControlState<List<RepeatableRow>>): Boolean {
        val maxRows = validationOptions?.maxItems
        return maxRows == null || controlState.value.value!!.size < maxRows
    }
    
    fun canDeleteRow(controlState: ControlState<List<RepeatableRow>>): Boolean {
        val minRows = validationOptions?.minItems ?: 0
        return controlState.value.value!!.size > minRows
    }
}