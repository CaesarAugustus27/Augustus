package org.octavius.modules.spotify.api

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.getOrElse
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.octavius.contract.ApiModule
import org.octavius.modules.spotify.model.TrashDescribeResponse
import org.octavius.modules.spotify.model.TrashDescriptions
import org.octavius.modules.spotify.model.TrashRemoveRequest
import org.octavius.modules.spotify.model.TrashRemoveResponse
import org.octavius.modules.spotify.model.TrashSyncResponse
import org.octavius.modules.spotify.model.TrashUris

/**
 * Endpointy kopii kosza. Kosz jest kopią zapasową LocalStorage klienta Spotify, więc synchronizacja tylko
 * dopisuje: wyczyszczony LocalStorage (np. reinstalacja Spotify) nie może wyczyścić kopii. Wpis znika tylko
 * przez jawne wyjęcie z kosza. LocalStorage zna same URI, opisy dosyła rozszerzenie do tych, których baza nie ma.
 */
class TrashApi : ApiModule, KoinComponent {
    private val db: OctaviusClient by inject()

    override fun installRoutes(routing: Routing) {
        routing.route("/api/spotify/trash") {
            contents()
            sync()
            describe()
            remove()
        }
    }

    /**
     * Definiuje endpoint: GET /api/spotify/trash
     * URI wszystkiego, co jest w koszu, do przywrócenia LocalStorage.
     */
    private fun Route.contents() {
        get {
            val tracks = db.rawQuery("SELECT uri FROM spotify.trashed_tracks").asResult()
                .fetchFields<String>().getOrElse { return@get respondFailure(it) }
            val artists = db.rawQuery("SELECT uri FROM spotify.artists WHERE trashed_at IS NOT NULL").asResult()
                .fetchFields<String>().getOrElse { return@get respondFailure(it) }
            call.respond(TrashUris(tracks, artists))
        }
    }

    /**
     * Definiuje endpoint: POST /api/spotify/trash/sync
     * Przyjmuje cały kosz: dopisuje brakujące wpisy i odsyła te z przysłanych URI, których opisu jeszcze nie ma.
     * Niczego nie usuwa.
     */
    private fun Route.sync() {
        post("/sync") {
            val trash = call.receive<TrashUris>()
            val row = db.rawQuery(SYNC_SQL).asResult().fetchRowStrict(
                "tracks" to trash.tracks.withPgType(PgStandardType.TEXT_ARRAY),
                "artists" to trash.artists.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@post respondFailure(it) }
            call.respond(TrashSyncResponse(row["added"], row["missing_tracks"], row["missing_artists"]))
        }
    }

    /**
     * Definiuje endpoint: POST /api/spotify/trash/describe
     * Zapisuje opisy utworów (tytuł, album, wykonawcy) i nazwy wykonawców z kosza. Opis wpisu, którego nie ma już ani
     * w koszu, ani w polubionych, przepada.
     */
    private fun Route.describe() {
        post("/describe") {
            when (val result = describe(call.receive<TrashDescriptions>())) {
                is DataResult.Success -> call.respond(TrashDescribeResponse(result.value))
                is DataResult.Failure -> respondFailure(result)
            }
        }
    }

    /**
     * Definiuje endpoint: POST /api/spotify/trash/remove
     * Wyjmuje utwory i wykonawców z kosza i sprząta utwory, wykonawców i albumy, do których nic już nie prowadzi.
     * Utwór z polubionych zostaje.
     */
    private fun Route.remove() {
        post("/remove") {
            val uris = call.receive<TrashRemoveRequest>().uris.withPgType(PgStandardType.TEXT_ARRAY)
            val result = db.transactionResult {
                val tracks = rawQuery("DELETE FROM spotify.trashed_tracks WHERE uri = ANY(@uris)").asResult()
                    .executeUpdate("uris" to uris).getOrElse { return@transactionResult it }
                val artists = rawQuery(UNTRASH_ARTISTS).asResult()
                    .executeUpdate("uris" to uris).getOrElse { return@transactionResult it }
                SpotifyCatalog.dropOrphans().getOrElse { return@transactionResult it }
                DataResult.Success((tracks + artists).toInt())
            }
            when (result) {
                is DataResult.Success -> call.respond(TrashRemoveResponse(result.value))
                is DataResult.Failure -> respondFailure(result)
            }
        }
    }

    private fun describe(descriptions: TrashDescriptions): DataResult<Int> =
        db.transactionResult {
            val described = SpotifyCatalog.describe(descriptions.tracks).getOrElse { return@transactionResult it }
            val named = rawQuery(NAME_ARTISTS).asResult().executeUpdate(
                "uris" to descriptions.artists.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY),
                "names" to descriptions.artists.map { it.name }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            DataResult.Success(described + named.toInt())
        }

    private suspend fun RoutingContext.respondFailure(result: DataResult.Failure) =
        call.respond(HttpStatusCode.InternalServerError, "Błąd bazy: ${result.error.message}")
}

/**
 * Dopisuje utwory i wykonawców z kosza (utwór i wykonawca znani już z polubionych dostają tylko wpis w koszu) i
 * odsyła URI bez opisu. Główne zapytanie widzi tabele sprzed wstawienia, więc nowy wpis wychodzi jako brak przez LEFT
 * JOIN, tak samo jak stary bez opisu.
 */
private const val SYNC_SQL = """
WITH incoming_tracks AS (
    SELECT uri FROM UNNEST(@tracks) AS t(uri)
),
     incoming_artists AS (
         SELECT uri FROM UNNEST(@artists) AS a(uri)
     ),
     known_tracks AS (
         INSERT INTO spotify.tracks (uri)
             SELECT uri FROM incoming_tracks
             ON CONFLICT (uri) DO NOTHING
     ),
     added_tracks AS (
         INSERT INTO spotify.trashed_tracks (uri)
             SELECT uri FROM incoming_tracks
             ON CONFLICT (uri) DO NOTHING
             RETURNING uri
     ),
     added_artists AS (
         INSERT INTO spotify.artists (uri, trashed_at)
             SELECT uri, CURRENT_TIMESTAMP FROM incoming_artists
             ON CONFLICT (uri) DO UPDATE SET trashed_at = CURRENT_TIMESTAMP WHERE artists.trashed_at IS NULL
             RETURNING uri
     )
SELECT ((SELECT count(*) FROM added_tracks) + (SELECT count(*) FROM added_artists))::int AS added,
       ARRAY(SELECT i.uri
             FROM incoming_tracks i
                      LEFT JOIN spotify.tracks t ON t.uri = i.uri
             WHERE t.title IS NULL)                                                  AS missing_tracks,
       ARRAY(SELECT i.uri
             FROM incoming_artists i
                      LEFT JOIN spotify.artists a ON a.uri = i.uri
             WHERE a.name IS NULL)                                                   AS missing_artists
"""

/** Nazwy wykonawców z kosza; tylko istniejącym, żeby opis wyjętego wykonawcy niczego nie dopisał. */
private const val NAME_ARTISTS = """
UPDATE spotify.artists a
SET name = i.name
FROM UNNEST(@uris, @names) AS i(uri, name)
WHERE a.uri = i.uri
  AND a.name IS DISTINCT FROM i.name
"""

private const val UNTRASH_ARTISTS = """
UPDATE spotify.artists SET trashed_at = NULL WHERE uri = ANY(@uris) AND trashed_at IS NOT NULL
"""
