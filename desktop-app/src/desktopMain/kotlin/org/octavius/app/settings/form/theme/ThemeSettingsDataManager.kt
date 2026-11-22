package org.octavius.app.settings.form.theme

import org.octavius.app.settings.AppSettingsManager
import org.octavius.app.settings.domain.AppThemeMode
import io.github.octaviusframework.form.FormDataManager
import io.github.octaviusframework.form.FormResultData
import io.github.octaviusframework.form.getCurrentAs

class ThemeSettingsDataManager(
    private val settingsManager: AppSettingsManager
) : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?> = mapOf(
        "theme" to settingsManager.currentSettings.theme
    )

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> = mapOf(
        "save" to { data ->
            val selectedTheme = data.getCurrentAs<AppThemeMode>("theme")
            val newSettings = settingsManager.currentSettings.copy(
                theme = selectedTheme
            )
            settingsManager.updateSettings(newSettings)
            true
        }
    )
}
