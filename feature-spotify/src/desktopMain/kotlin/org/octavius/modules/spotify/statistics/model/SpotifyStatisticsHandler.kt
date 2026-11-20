package org.octavius.modules.spotify.statistics.model

import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

/**
 * Pierwsza migracja schematu `spotify`. Od tej chwili `trashed_at` to dotarcie utworu do kopii. Utwory sprzed niej
 * mają datę eksportu, w którym pojawiły się pierwszy raz, więc daty kosza liczą się dopiero od niej.
 */
internal val TRASH_COPY_STARTED_AT: Instant = Instant.parse("2026-11-06T11:27:24Z")

/**
 * Ładuje statystyki w [scope]. Stałe zapytanie bez danych od użytkownika może paść tylko przy niedostępnej bazie albo
 * błędzie w kodzie, więc wyjątek idzie do `showUnhandledError` scope'u, a [data] zostaje `null`.
 */
class SpotifyStatisticsHandler(private val scope: CoroutineScope) : KoinComponent {
    private val db: OctaviusClient by inject()

    private val _data = MutableStateFlow<SpotifyStatisticsData?>(null)

    /** `null`, dopóki statystyki się nie wczytają. */
    val data = _data.asStateFlow()

    fun loadData() {
        scope.launch {
            _data.value = withContext(OctaviusDispatchers.Virtual) {
                db.rawQuery(STATISTICS_QUERY)
                    .fetchObjectStrict<SpotifyStatisticsData>("copyStartedAt" to TRASH_COPY_STARTED_AT)
            }
        }
    }
}

/**
 * Lata i tygodnie idą przez `generate_series`, żeby okres bez utworów dał słupek 0, a nie dziurę. Tygodni jest
 * najwyżej 12, od tygodnia początku kopii. Album łączy utwory tylko z pierwszym wykonawcą, żeby każdy liczył się raz.
 */
private const val STATISTICS_QUERY = """
SELECT
    (SELECT count(*) FROM spotify.trashed_tracks) AS trashed_count,
    (SELECT count(*) FROM spotify.liked_tracks) AS liked_count,
    (SELECT count(*) FROM spotify.artists) AS artist_count,
    (SELECT count(*) FROM spotify.albums) AS album_count,
    (SELECT count(*) FROM spotify.trashed_tracks WHERE trashed_at >= @copyStartedAt) AS trashed_since_copy,
    (SELECT COALESCE(array_agg(ROW('year', y.year, 'count', y.count) ORDER BY y.year), '{}')
     FROM (SELECT year::int, count(l.uri) AS count
           FROM generate_series((SELECT min(extract(year FROM liked_at)) FROM spotify.liked_tracks),
                                (SELECT max(extract(year FROM liked_at)) FROM spotify.liked_tracks)) AS year
                    LEFT JOIN spotify.liked_tracks l ON extract(year FROM l.liked_at) = year
           GROUP BY year) y) AS liked_by_year,
    (SELECT COALESCE(array_agg(ROW('week_start', w.week_start, 'count', w.count) ORDER BY w.week_start), '{}')
     FROM (SELECT week::date AS week_start, count(t.uri) AS count
           FROM generate_series(greatest(date_trunc('week', @copyStartedAt), date_trunc('week', now()) - interval '11 weeks'),
                                date_trunc('week', now()), interval '1 week') AS week
                    LEFT JOIN spotify.trashed_tracks t
                              ON t.trashed_at >= @copyStartedAt AND date_trunc('week', t.trashed_at) = week
           GROUP BY week) w) AS trashed_by_week,
    (SELECT COALESCE(array_agg(ROW('name', a.name, 'artist', a.artist, 'trashed', a.trashed, 'liked', a.liked)
                               ORDER BY a.trashed DESC, a.liked, a.name), '{}')
     FROM (SELECT al.name,
                  mode() WITHIN GROUP (ORDER BY ar.name) AS artist,
                  count(tt.uri) AS trashed,
                  count(lt.uri) AS liked
           FROM spotify.albums al
                    JOIN spotify.tracks t ON t.album_uri = al.uri
                    LEFT JOIN spotify.track_artists ta ON ta.track_uri = t.uri AND ta.position = 0
                    LEFT JOIN spotify.artists ar ON ar.uri = ta.artist_uri
                    LEFT JOIN spotify.trashed_tracks tt ON tt.uri = t.uri
                    LEFT JOIN spotify.liked_tracks lt ON lt.uri = t.uri
           GROUP BY al.uri, al.name
           ORDER BY trashed DESC, liked, al.name
           LIMIT 10) a) AS top_trashed_albums
"""
