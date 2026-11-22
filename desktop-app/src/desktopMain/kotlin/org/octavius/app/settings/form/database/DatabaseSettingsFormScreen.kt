package org.octavius.app.settings.form.database

import org.octavius.app.localization.AppTr
import org.octavius.app.settings.AppSettingsManager
import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class DatabaseSettingsFormScreen {
    companion object {
        fun create(settingsManager: AppSettingsManager): Screen {
            val title = AppTr.Settings.Database.title()
            val formHandler = FormHandler(
                formSchemaBuilder = DatabaseSettingsSchemaBuilder(),
                formDataManager = DatabaseSettingsDataManager(settingsManager)
            )
            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
