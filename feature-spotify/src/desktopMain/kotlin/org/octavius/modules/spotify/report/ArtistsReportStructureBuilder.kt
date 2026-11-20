package org.octavius.modules.spotify.report

import io.github.octaviusframework.client.query.QueryFragment
import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.report.column.ReportColumn
import org.octavius.report.column.type.BigDecimalColumn
import org.octavius.report.column.type.BooleanColumn
import org.octavius.report.column.type.LongColumn
import org.octavius.report.column.type.StringColumn
import org.octavius.report.component.ReportStructureBuilder

/** Wykonawcy z liczbą polubionych utworów i utworów w koszu. Utwór liczy się u każdego wykonawcy, który w nim gra. */
class ArtistsReportStructureBuilder : ReportStructureBuilder() {

    override fun getReportName(): String = "spotify_artists"

    override fun buildQuery(): QueryFragment = QueryFragment(sql = ARTISTS_QUERY)

    override fun buildColumns(): Map<String, ReportColumn> = mapOf(
        "name" to StringColumn(header = SpotifyTr.Artists.name(), width = 2f),
        "liked" to LongColumn(header = SpotifyTr.Artists.liked()),
        "trashed" to LongColumn(header = SpotifyTr.Artists.trashed()),
        "total" to LongColumn(header = SpotifyTr.Artists.total()),
        "liked_share" to BigDecimalColumn(
            header = SpotifyTr.Artists.likedShare(),
            formatter = { share -> share?.let { "${it.toPlainString()}%" } ?: "" }
        ),
        "artist_trashed" to BooleanColumn(header = SpotifyTr.Artists.artistTrashed())
    )
}

/**
 * `total` to utwory wykonawcy w kopii, czyli w koszu albo w polubionych. `liked_share` to procent polubionych wśród
 * nich, `null` u wykonawcy z kosza bez żadnego utworu. Bez wybranego sortowania na górze są wykonawcy z największą
 * liczbą utworów.
 */
private const val ARTISTS_QUERY = """
SELECT a.name,
       count(DISTINCT lt.uri) AS liked,
       count(DISTINCT tt.uri) AS trashed,
       count(DISTINCT ta.track_uri) AS total,
       round(100.0 * count(DISTINCT lt.uri) / NULLIF(count(DISTINCT ta.track_uri), 0), 1) AS liked_share,
       a.trashed_at IS NOT NULL AS artist_trashed
FROM spotify.artists a
         LEFT JOIN spotify.track_artists ta ON ta.artist_uri = a.uri
         LEFT JOIN spotify.trashed_tracks tt ON tt.uri = ta.track_uri
         LEFT JOIN spotify.liked_tracks lt ON lt.uri = ta.track_uri
GROUP BY a.uri
ORDER BY total DESC, a.name
"""
