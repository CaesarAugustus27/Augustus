package org.octavius.modules.games.form.series

import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.Control
import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.container.SectionControl
import io.github.octaviusframework.form.control.number.IntegerControl
import io.github.octaviusframework.form.control.primitive.StringControl
import org.octavius.localization.Tr
import org.octavius.modules.games.localization.GamesTr
import org.octavius.navigation.AppRouter

class GameSeriesFormSchemaBuilder : FormSchemaBuilder() {
    override fun defineContentOrder(): List<String> = listOf("basic_info")
    override fun defineActionBarOrder(): List<String> = listOf("cancel_button", "save_button")

    override fun defineControls(): Map<String, Control<*>> =
        mapOf(
            "id" to IntegerControl(null),
            "basic_info" to SectionControl(
                controls = mapOf(
                    "name" to StringControl(
                        GamesTr.Series.name(),
                        required = true
                    )
                ),
                collapsible = false,
                initiallyExpanded = true,
                columns = 1,
                label = GamesTr.Series.basicInfo()
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