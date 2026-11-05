package org.octavius.app.settings

import org.octavius.contract.ApiModule
import org.octavius.app.settings.navigation.SettingsTab
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.navigation.Tab

object SettingsFeature : FeatureModule {
    override val name: String = "settings"

    // Zakładka jest ukryta, a pierwsza na liście otwiera się po starcie - więc ustawienia idą na koniec.
    override val order: Int = 1000

    // Tabele ustawień są w public, który migruje się zawsze, niezależnie od featurów.
    override val schema: String? = null

    override fun getTab(): Tab = SettingsTab()
    override fun getApiModules(): List<ApiModule>? = null
    override fun getScreenFactories(): List<ScreenFactory>? = null
}