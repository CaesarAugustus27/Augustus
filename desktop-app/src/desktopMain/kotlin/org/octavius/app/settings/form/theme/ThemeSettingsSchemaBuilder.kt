package org.octavius.app.settings.form.theme

import org.octavius.app.localization.AppTr
import org.octavius.app.settings.domain.AppThemeMode
import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.Control
import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.selection.EnumControl

class ThemeSettingsSchemaBuilder : FormSchemaBuilder() {
    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "theme" to EnumControl(
            label = AppTr.Settings.Theme.select(),
            enumClass = AppThemeMode::class,
            required = true
        ),
        "save" to ButtonControl(
            text = AppTr.Settings.Theme.save(),
            buttonType = ButtonType.Filled,
            actions = listOf(ControlAction {
                trigger.triggerAction("save", validates = true)
            })
        )
    )

    override fun defineContentOrder(): List<String> = listOf("theme")

    override fun defineActionBarOrder(): List<String> = listOf("save")
}
