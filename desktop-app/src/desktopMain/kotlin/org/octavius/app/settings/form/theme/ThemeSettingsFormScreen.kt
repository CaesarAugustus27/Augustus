package org.octavius.app.settings.form.theme

import org.octavius.app.localization.AppTr
import org.octavius.app.settings.AppSettingsManager
import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class ThemeSettingsFormScreen {
    companion object {
        fun create(settingsManager: AppSettingsManager): Screen {
            val title = AppTr.Settings.Theme.title()
            val formHandler = FormHandler(
                formSchemaBuilder = ThemeSettingsSchemaBuilder(),
                formDataManager = ThemeSettingsDataManager(settingsManager)
            )
            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
