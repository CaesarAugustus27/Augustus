package org.octavius.modules.spotify

import org.octavius.contract.ApiModule
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.modules.spotify.api.TrashbinApi
import org.octavius.navigation.Tab

/**
 * Kosz ze Spicetify. Bez zakładki: trashbin.js wysyła kosz sam, a dane są w schemacie
 * `spotify`.
 */
object SpotifyFeature : FeatureModule {
    override val name: String = "spotify"
    override val order: Int = 40
    override val schema: String = "spotify"
    override fun getTab(): Tab? = null
    override fun getApiModules(): List<ApiModule> = listOf(TrashbinApi())
    override fun getScreenFactories(): List<ScreenFactory>? = null
}
