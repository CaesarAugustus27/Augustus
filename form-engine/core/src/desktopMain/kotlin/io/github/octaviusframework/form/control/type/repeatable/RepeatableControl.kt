package io.github.octaviusframework.form.control.type.repeatable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import io.github.octaviusframework.form.component.ErrorManager
import io.github.octaviusframework.form.component.FormActionTrigger
import io.github.octaviusframework.form.component.FormSchema
import io.github.octaviusframework.form.component.FormState
import io.github.octaviusframework.form.component.InitDataException
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.layout.repeatable.RepeatableHeader
import io.github.octaviusframework.form.control.layout.repeatable.RepeatableRowCard
import io.github.octaviusframework.form.control.layout.repeatable.RepeatableRowContent
import io.github.octaviusframework.form.control.validator.repeatable.RepeatableValidator
import org.octavius.theme.FormSpacing

/**
 * A control for dynamic lists of controls (rows).
 *
 * Lets the user add and delete rows, where every row holds a set of controls. Supports validating field
 * uniqueness, minimum and maximum row counts, and collapsing/expanding rows. Every row can be edited and
 * deleted on its own.
 */
class RepeatableControl(
    val rowControls: Map<String, Control<*>>,
    val rowOrder: List<String>,
    label: String?,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    validationOptions: RepeatableValidation? = null
) : Control<List<RepeatableRow>>(
    label,
    required,
    dependencies,
    hasStandardLayout = false,
    validationOptions = validationOptions
) {
    private lateinit var rowManager: RepeatableRowManager

    override fun initializeControlLifecycle(
        formState: FormState,
        formSchema: FormSchema,
        errorManager: ErrorManager,
        formActionTrigger: FormActionTrigger
    ) {
        super.initializeControlLifecycle(formState, formSchema, errorManager, formActionTrigger)
        this.rowManager = RepeatableRowManager(rowControls, formState, validationOptions as RepeatableValidation?)
    }

    override fun registerChildrenInGlobalMap(controlContext: ControlContext): Map<String, Control<*>> {
        val map = mutableMapOf<String, Control<*>>()
        rowControls.forEach { (key, value) ->
            // rowId is not used here
            val childControlContext = controlContext.forRepeatableChild(key, "")
            map[childControlContext.fullControlPath] = value
            map.putAll(value.registerChildrenInGlobalMap(childControlContext))
        }
        return map
    }

    override fun childContexts(controlContext: ControlContext): List<Pair<ControlContext, Control<*>>> {
        @Suppress("UNCHECKED_CAST")
        val rows = formState.getControlState(controlContext.fullStatePath)!!.value.value as List<RepeatableRow>
        return rows.flatMap { row -> rowContexts(controlContext, row, rowControls) }
    }

    override val validator: ControlValidator<List<RepeatableRow>> = RepeatableValidator(validationOptions)

    override fun copyInitToValue(value: List<RepeatableRow>): List<RepeatableRow> {
        // With the global state there is no need to copy the states - they are already in FormState
        return value.map { RepeatableRow(id = it.id, index = it.index) }
    }

    override fun acceptsValue(value: Any) = false

    override fun setInitValue(controlContext: ControlContext, value: Any?): ControlState<List<RepeatableRow>> {
        if (value != null && !(value is List<*> && value.all { it is Map<*, *> })) {
            throw InitDataException(controlContext.fullControlPath, mismatch(value))
        }
        @Suppress("UNCHECKED_CAST")
        val initialDataRows = value as List<Map<String, Any?>>? ?: emptyList()

        // 1. Create rows and states for the initial data
        val initialModelRows = initialDataRows.mapIndexed { index, dataRow ->
            createRow(index, controlContext, rowControls, formState, dataRow)
        }

        // 2. Add empty rows if minItems requires them
        val additionalModelRows = mutableListOf<RepeatableRow>()
        val minRows = (validationOptions as? RepeatableValidation)?.minItems ?: 0
        while (initialModelRows.size + additionalModelRows.size < minRows) {
            val index = initialModelRows.size + additionalModelRows.size

            val newRow = createRow(index, controlContext, rowControls, formState)
            additionalModelRows.add(newRow)
        }

        // 3. Return the state of the RepeatableControl itself
        val allInitialRows = initialModelRows + additionalModelRows
        return ControlState(
            initValue = mutableStateOf(initialModelRows),
            value = mutableStateOf(copyInitToValue(allInitialRows)),
        )
    }

    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<List<RepeatableRow>>, isRequired: Boolean) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RepeatableHeader(
                label = label,
                onAddClick = {
                    rowManager.addRow(controlContext, controlState)
                },
                canAdd = rowManager.canAddRow(controlState)
            )

            DisplayFieldErrors(controlContext)

            Spacer(modifier = Modifier.height(FormSpacing.itemSpacing))

            controlState.value.value?.forEachIndexed { index, row ->
                // Without a key Compose assigns state (`remember`) by position: after deleting a row from
                // the middle, the card's collapsed state and the controls' UI state would move to the row
                // that took its place.
                key(row.id) {
                    RepeatableRowCard(
                        index = index,
                        canDelete = rowManager.canDeleteRow(controlState),
                        onDelete = {
                            rowManager.deleteRow(controlContext, controlState, row.id)
                        },
                        content = {
                            RepeatableRowContent(
                                row = row,
                                controlContext = controlContext,
                                rowOrder = rowOrder,
                                rowControls = rowControls,
                                formState = formState
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(FormSpacing.itemSpacing))
                }
            }
        }
    }

    override fun convertToResult(
        controlContext: ControlContext,
        state: ControlState<*>, // For RepeatableControl this state is ControlState<List<RepeatableRow>>
    ): ControlResultData {
        @Suppress("UNCHECKED_CAST")
        val controlState = state as ControlState<List<RepeatableRow>>

        val (newRows, deletedRows, changedRows) = getRowTypes(
            controlState,
            controlContext,
            rowControls,
            formState
        )

        fun rowValues(row: RepeatableRow): FormResultData =
            formState.collectLevel(rowContexts(controlContext, row, rowControls))

        // Collecting data changes nothing in the state. The states of deleted database rows have to survive,
        // because their initial values go to deletedRows on EVERY collection, and that repeats when a save
        // fails and the user clicks again.
        return ControlResultData(
            currentValue = RepeatableResultValue(
                deletedRows = deletedRows.map(::rowValues),
                addedRows = newRows.map(::rowValues),
                modifiedRows = changedRows.map(::rowValues),
                allCurrentRows = controlState.value.value!!.map(::rowValues)
            ),
            initialValue = null  // The original values are already in currentValue of the individual controls
        )
    }
}