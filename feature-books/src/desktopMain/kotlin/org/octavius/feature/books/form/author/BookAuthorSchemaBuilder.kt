package org.octavius.feature.books.form.author

import kotlinx.coroutines.launch
import org.octavius.dialog.DialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.feature.books.localization.BooksTr
import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.*
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.number.IntegerControl
import io.github.octaviusframework.form.control.primitive.StringControl
import org.octavius.localization.Tr
import org.octavius.navigation.AppRouter

class BookAuthorSchemaBuilder : FormSchemaBuilder() {

    override fun defineContentOrder(): List<String> = listOf("name", "sort_name")
    override fun defineActionBarOrder(): List<String> = listOf("cancel_button", "save_button", "delete_button")

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(null),
        "name" to StringControl(
            BooksTr.Authors.Form.name(),
            required = true
        ),
        "sort_name" to StringControl(
            BooksTr.Authors.Form.sortName(),
            required = false
        ),
        // Przyciski
        "save_button" to ButtonControl(
            text = Tr.Action.save(),
            buttonType = ButtonType.Filled,
            actions = listOf(
                ControlAction {
                    if (trigger.triggerAction("save", true)) AppRouter.goBack()
                }
            )
        ),
        "delete_button" to ButtonControl(
            text = Tr.Action.remove(),
            buttonType = ButtonType.Filled,
            dependencies = mapOf(
                "visible" to ControlDependency(
                    controlPath = "id",
                    value = null,
                    dependencyType = DependencyType.Visible,
                    comparisonType = ComparisonType.NotEquals
                )
            ),
            actions = listOf(
                ControlAction {
                    GlobalDialogManager.show(
                        DialogConfig(
                            title = Tr.Action.confirm(),
                            text = BooksTr.Authors.Form.confirmDelete(),
                            onDismiss = { GlobalDialogManager.dismiss() },
                            confirmButtonText = Tr.Action.confirm(),
                            onConfirm = {
                                coroutineScope.launch {
                                    if (trigger.triggerAction("delete", false)) AppRouter.goBack()
                                    GlobalDialogManager.dismiss()
                                }
                            }
                        )
                    )
                }
            )
        ),
        "cancel_button" to ButtonControl(
            text = Tr.Action.cancel(),
            buttonType = ButtonType.Outlined,
            actions = listOf(
                ControlAction {
                    AppRouter.goBack()
                }
            )
        )
    )
}