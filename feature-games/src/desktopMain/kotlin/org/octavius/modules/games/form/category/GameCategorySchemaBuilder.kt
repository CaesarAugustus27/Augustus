package org.octavius.modules.games.form.category

import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.Control
import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.number.IntegerControl
import io.github.octaviusframework.form.control.primitive.StringControl
import org.octavius.localization.Tr
import org.octavius.modules.games.localization.GamesTr
import org.octavius.navigation.AppRouter

class GameCategorySchemaBuilder : FormSchemaBuilder() {

    override fun defineActionBarOrder(): List<String> = listOf("cancel_button", "save_button")
    override fun defineContentOrder(): List<String> = listOf("name")

    override fun defineControls(): Map<String, Control<*>> =
        mapOf(
            "id" to IntegerControl(
                null
            ),
            "name" to StringControl(
                GamesTr.Categories.name(),
                required = true
            ),
            // Przyciski
            "save_button" to ButtonControl(
                text = Tr.Action.save(), actions = listOf(
                    ControlAction {
                        if (trigger.triggerAction("save", true)) AppRouter.goBack()
                    }
                ), buttonType = ButtonType.Filled
            ),
            "cancel_button" to ButtonControl(
                text = Tr.Action.cancel(), buttonType = ButtonType.Outlined, actions = listOf(
                    ControlAction {
                        AppRouter.goBack()
                    }
                )
            )
        )
}