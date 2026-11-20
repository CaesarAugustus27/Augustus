package org.octavius.modules.spotify.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.modules.spotify.statistics.ui.SpotifyStatisticsScreen
import org.octavius.navigation.Screen
import org.octavius.navigation.Tab
import org.octavius.navigation.TabOptions

class SpotifyTab : Tab {
    override val id = "spotify"

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            title = SpotifyTr.tab(),
            icon = rememberVectorPainter(Icons.Filled.MusicNote)
        )

    override fun getInitialScreen(): Screen = SpotifyStatisticsScreen.create()
}
