package io.github.octaviusframework.form.control.type.repeatable

import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.control.base.Control
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.control.base.FormResultData

/**
 * One row of a repeatable control. Its state lives in the global FormState.
 *
 * @param id unique row identifier (generated automatically)
 * @param index position of the row in the list (used to build hierarchical names)
 */
data class RepeatableRow(
    val id: String = java.util.UUID.randomUUID().toString(),
    val index: Int = 0
)

/**
 * Result of a repeatable control, split by operation type.
 *
 * @param deletedRows rows to delete from the database
 * @param addedRows new rows to add
 * @param modifiedRows rows to update
 */
data class RepeatableResultValue(
    val deletedRows: List<FormResultData>,
    val addedRows: List<FormResultData>,
    val modifiedRows: List<FormResultData>,
    val allCurrentRows: List<FormResultData>
)

/**
 * The row's controls, each with a context in which the repeatable control is the parent.
 * A row is a separate data level: the states of its controls lie under `name[rowId]/...`.
 */
internal fun rowContexts(
    parentContext: ControlContext,
    row: RepeatableRow,
    rowControls: Map<String, Control<*>>
): List<Pair<ControlContext, Control<*>>> {
    return rowControls.map { (controlName, control) -> parentContext.forRepeatableChild(controlName, row.id) to control }
}

/**
 * Creates a new row and adds the states of its controls to the global FormState.
 *
 * @param index position of the row in the list
 * @param parentContext context of the repeatable control
 * @param rowControls map of the controls that make up the row
 * @param formState global form state
 * @param values initial values of the row's fields (key = control name), empty for a new row
 * @return the new row with its index set
 */
internal fun createRow(
    index: Int,
    parentContext: ControlContext, // E.g. "publications" or "projects[uuid].tasks"
    rowControls: Map<String, Control<*>>,
    formState: FormState,
    values: Map<String, Any?> = emptyMap()
): RepeatableRow {
    val row = RepeatableRow(index = index)
    formState.initializeLevel(parentContext.fullControlPath, rowContexts(parentContext, row, rowControls), values)
    return row
}

/**
 * Analyzes the state of a repeatable control and classifies the rows by operation type.
 * Works on the global form state.
 *
 * @param controlState state of the repeatable control
 * @param controlContext context of the repeatable control (for building hierarchical names)
 * @param rowControls map of the controls in a row
 * @param formState global form state
 * @return Triple(new rows, deleted rows, changed rows)
 */
internal fun getRowTypes(
    controlState: ControlState<List<RepeatableRow>>,
    controlContext: ControlContext,
    rowControls: Map<String, Control<*>>,
    formState: FormState
): Triple<List<RepeatableRow>, List<RepeatableRow>, List<RepeatableRow>> {
    val currentRowIds = controlState.value.value!!.map { it.id }.toSet()
    val initialRowIds = controlState.initValue.value!!.map { it.id }.toSet()

    // New rows: those in current whose ID is not in initial
    val newRows = controlState.value.value!!.filter { it.id !in initialRowIds }

    // Deleted rows: those in initial whose ID is not in current
    val deletedRows = controlState.initValue.value!!.filter { it.id !in currentRowIds }

    // Changed rows: those in both lists (by ID) AND with at least one "dirty" field
    val changedRows = controlState.value.value!!.filter { currentRow ->
        if (currentRow.id !in initialRowIds) return@filter false

        rowContexts(controlContext, currentRow, rowControls).any { (fieldContext, field) ->
            isChanged(fieldContext, field, formState)
        }
    }
    return Triple(newRows, deletedRows, changedRows)
}

// A control is changed when its value changed or anything below it did - a section field in the row or
// a row of a nested repeatable.
private fun isChanged(controlContext: ControlContext, control: Control<*>, formState: FormState): Boolean {
    val state = formState.getControlState(controlContext.fullStatePath)!!
    return state.initValue.value != state.value.value ||
        control.childContexts(controlContext).any { (childContext, child) -> isChanged(childContext, child, formState) }
}