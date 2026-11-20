package org.octavius.modules.spotify

import org.octavius.contract.ApiModule
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.modules.spotify.api.LikedApi
import org.octavius.modules.spotify.api.TrashApi
import org.octavius.modules.spotify.navigation.SpotifyTab
import org.octavius.navigation.Tab

/**
 * Kosz i polubione ze Spotify. Rozszerzenie Spicetify (augustus.js) wysyła je samo, a dane są w schemacie `spotify`.
 * Zakładka tylko je pokazuje: statystyki i raport wykonawców.
 */
object SpotifyFeature : FeatureModule {
    override val name: String = "spotify"
    override val order: Int = 40
    override val schema: String = "spotify"
    override fun getTab(): Tab = SpotifyTab()
    override fun getApiModules(): List<ApiModule> = listOf(TrashApi(), LikedApi())
    override fun getScreenFactories(): List<ScreenFactory>? = null
}
