package org.octavius.app.settings.form.language

import io.github.octaviusframework.form.FormDataManager
import io.github.octaviusframework.form.FormResultData
import io.github.octaviusframework.form.getCurrentAs
import org.octavius.app.settings.AppSettingsManager
import org.octavius.app.settings.domain.AppLanguage

class LanguageSettingsDataManager(
    private val settingsManager: AppSettingsManager
) : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?> {
        val currentLangCode = settingsManager.currentSettings.language
        return mapOf(
            "language" to AppLanguage.fromCode(currentLangCode)
        )
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> = mapOf(
        "save" to { data ->
            val selectedLang = data.getCurrentAs<AppLanguage>("language")
            val newSettings = settingsManager.currentSettings.copy(
                language = selectedLang.code
            )
            settingsManager.updateSettings(newSettings)
            true
        }
    )
}
