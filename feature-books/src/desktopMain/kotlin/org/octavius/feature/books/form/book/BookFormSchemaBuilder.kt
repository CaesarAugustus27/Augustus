package org.octavius.feature.books.form.book

import io.github.octaviusframework.client.query.QueryFragment
import kotlinx.coroutines.launch
import org.octavius.dialog.DialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.feature.books.domain.ReadingStatus
import org.octavius.feature.books.localization.BooksTr
import io.github.octaviusframework.form.component.FormSchemaBuilder
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.control.type.button.ButtonControl
import io.github.octaviusframework.form.control.type.button.ButtonType
import io.github.octaviusframework.form.control.type.container.SectionControl
import io.github.octaviusframework.form.control.type.number.IntegerControl
import io.github.octaviusframework.form.control.type.primitive.StringControl
import io.github.octaviusframework.form.control.type.repeatable.RepeatableControl
import io.github.octaviusframework.form.database.DatabaseControl
import io.github.octaviusframework.form.control.type.selection.EnumControl
import org.octavius.localization.Tr
import org.octavius.navigation.AppRouter

class BookFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineContentOrder(): List<String> = listOf("basic_info", "authors")
    override fun defineActionBarOrder(): List<String> = listOf("cancel_button", "save_button", "delete_button")

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(null),

        // Podstawowe informacje
        "basic_info" to SectionControl(
            controls = mapOf(
                "title_pl" to StringControl(
                    BooksTr.Form.titlePl(),
                    required = true
                ),
                "title_eng" to StringControl(
                    BooksTr.Form.titleEng(),
                    required = false
                ),
                "status" to EnumControl(
                    BooksTr.Form.status(),
                    ReadingStatus::class,
                    required = true
                )
            ),
            collapsible = false,
            initiallyExpanded = true,
            columns = 1,
            label = BooksTr.Form.basicInfo()
        ),

        // Autorzy
        "authors" to RepeatableControl(
            rowControls = mapOf(
                "author_id" to DatabaseControl(
                    label = BooksTr.Form.author(),
                    query = QueryFragment("SELECT id, name FROM books.authors"),
                    displayColumn = "name",
                    required = true
                )
            ),
            rowOrder = listOf("author_id"),
            label = BooksTr.Form.authors(),
            validationOptions = RepeatableValidation(
                minItems = 0,
                maxItems = 10,
                uniqueFields = listOf("author_id")
            )
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
                            text = BooksTr.Form.confirmDelete(),
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