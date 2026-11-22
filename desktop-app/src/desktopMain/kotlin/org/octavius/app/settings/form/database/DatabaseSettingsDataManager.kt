package org.octavius.app.settings.form.database

import io.github.octaviusframework.form.FormDataManager
import io.github.octaviusframework.form.FormResultData
import io.github.octaviusframework.form.getCurrentAs
import org.octavius.app.settings.AppSettingsManager
import org.octavius.app.settings.domain.DatabaseSettings

class DatabaseSettingsDataManager(
    private val settingsManager: AppSettingsManager
) : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?> {
        val dbSettings = settingsManager.currentSettings.database
        return mapOf(
            "url" to dbSettings.url,
            "username" to dbSettings.username,
            "password" to dbSettings.password
        )
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> = mapOf(
        "save" to { data ->
            val currentSettings = settingsManager.currentSettings
            val newSettings = currentSettings.copy(
                database = DatabaseSettings(
                    url = data.getCurrentAs<String>("url"),
                    username = data.getCurrentAs<String>("username"),
                    password = data.getCurrentAs<String>("password")
                )
            )
            settingsManager.updateSettings(newSettings)
            true
        }
    )
}
