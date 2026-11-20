package org.octavius.modules.spotify.report.ui

import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.modules.spotify.report.ArtistsReportStructureBuilder
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen
import org.octavius.report.component.ReportHandler
import org.octavius.report.component.ReportView

class ArtistsReportScreen {
    companion object {
        fun create(): Screen {
            val reportHandler = ReportHandler(ArtistsReportStructureBuilder())
            return ComponentScreen(SpotifyTr.Artists.title()) { ReportView(reportHandler) }
        }
    }
}
