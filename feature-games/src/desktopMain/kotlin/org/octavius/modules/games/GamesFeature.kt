package org.octavius.modules.games

import org.octavius.contract.ApiModule
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.modules.games.api.GamesApi
import org.octavius.modules.games.navigation.GameTab
import org.octavius.navigation.Tab

object GamesFeature : FeatureModule {
    override val name: String = "games"
    override val order: Int = 20
    override val schema: String = "games"
    override fun getTab(): Tab = GameTab()
    override fun getApiModules(): List<ApiModule> = listOf(GamesApi())
    override fun getScreenFactories(): List<ScreenFactory>? = null
}
