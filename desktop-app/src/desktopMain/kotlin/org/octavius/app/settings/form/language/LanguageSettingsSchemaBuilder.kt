package org.octavius.app.settings.form.language

import org.octavius.app.localization.AppTr
import io.github.octaviusframework.form.FormSchemaBuilder
import io.github.octaviusframework.form.control.Control
import io.github.octaviusframework.form.control.ControlAction
import io.github.octaviusframework.form.control.button.ButtonControl
import io.github.octaviusframework.form.control.button.ButtonType
import io.github.octaviusframework.form.control.selection.EnumControl
import org.octavius.app.settings.domain.AppLanguage

class LanguageSettingsSchemaBuilder : FormSchemaBuilder() {
    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "language" to EnumControl(
            label = AppTr.Settings.Language.select(),
            enumClass = AppLanguage::class,
            required = true
        ),
        "save" to ButtonControl(
            text = AppTr.Settings.Language.save(),
            buttonType = ButtonType.Filled,
            actions = listOf(ControlAction {
                trigger.triggerAction("save", validates = true)
            })
        )
    )

    override fun defineContentOrder(): List<String> = listOf("language")

    override fun defineActionBarOrder(): List<String> = listOf("save")
}
