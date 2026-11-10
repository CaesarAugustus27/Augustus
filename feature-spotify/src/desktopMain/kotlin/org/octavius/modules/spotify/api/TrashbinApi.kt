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
 * Endpointy dla Trashbina. Kosz jest kopią zapasową LocalStorage klienta Spotify, więc synchronizacja tylko
 * dopisuje: wyczyszczony LocalStorage (np. reinstalacja Spotify) nie może wyczyścić kopii. Wpis znika tylko
 * przez jawne wyjęcie z kosza. LocalStorage zna same URI, opisy dosyła Trashbin do tych, których baza nie ma.
 */
class TrashbinApi : ApiModule, KoinComponent {
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
     * Zapisuje opisy utworów (tytuł, album, wykonawcy) i nazwy wykonawców z kosza. Opis wpisu, którego w koszu już
     * nie ma, przepada.
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
     * Usuwa wyjęte utwory, wykonawców wyjmuje z kosza i sprząta wykonawców i albumy, do których nic już nie
     * prowadzi.
     */
    private fun Route.remove() {
        post("/remove") {
            val uris = call.receive<TrashRemoveRequest>().uris.withPgType(PgStandardType.TEXT_ARRAY)
            val result = db.transactionResult {
                val tracks = rawQuery("DELETE FROM spotify.trashed_tracks WHERE uri = ANY(@uris)").asResult()
                    .executeUpdate("uris" to uris).getOrElse { return@transactionResult it }
                val artists = rawQuery(UNTRASH_ARTISTS).asResult()
                    .executeUpdate("uris" to uris).getOrElse { return@transactionResult it }
                rawQuery(DROP_ORPHANS).asResult().executeUpdate().getOrElse { return@transactionResult it }
                DataResult.Success((tracks + artists).toInt())
            }
            when (result) {
                is DataResult.Success -> call.respond(TrashRemoveResponse(result.value))
                is DataResult.Failure -> respondFailure(result)
            }
        }
    }

    private fun describe(descriptions: TrashDescriptions): DataResult<Int> {
        val tracks = descriptions.tracks
        val albums = tracks.mapNotNull { it.album }.distinctBy { it.uri }
        val credited = tracks.flatMap { it.artists }.distinctBy { it.uri }
        val credits = tracks.flatMap { track -> track.artists.mapIndexed { position, artist -> Triple(track.uri, position, artist.uri) } }
        val trackUris = tracks.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY)

        // Najpierw albumy i wykonawcy, bo utwory i ich wykonawcy się do nich odwołują
        return db.transactionResult {
            rawQuery(UPSERT_ALBUMS).asResult().executeUpdate(
                "uris" to albums.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY),
                "names" to albums.map { it.name }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            rawQuery(UPSERT_ARTISTS).asResult().executeUpdate(
                "uris" to credited.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY),
                "names" to credited.map { it.name }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            val described = rawQuery(DESCRIBE_TRACKS).asResult().executeUpdate(
                "uris" to trackUris,
                "titles" to tracks.map { it.title }.withPgType(PgStandardType.TEXT_ARRAY),
                "albums" to tracks.map { it.album?.uri }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            rawQuery("DELETE FROM spotify.track_artists WHERE track_uri = ANY(@uris)").asResult()
                .executeUpdate("uris" to trackUris).getOrElse { return@transactionResult it }
            rawQuery(INSERT_CREDITS).asResult().executeUpdate(
                "tracks" to credits.map { it.first }.withPgType(PgStandardType.TEXT_ARRAY),
                "positions" to credits.map { it.second }.withPgType(PgStandardType.INT4_ARRAY),
                "artists" to credits.map { it.third }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            val named = rawQuery(NAME_ARTISTS).asResult().executeUpdate(
                "uris" to descriptions.artists.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY),
                "names" to descriptions.artists.map { it.name }.withPgType(PgStandardType.TEXT_ARRAY)
            ).getOrElse { return@transactionResult it }
            DataResult.Success((described + named).toInt())
        }
    }

    private suspend fun RoutingContext.respondFailure(result: DataResult.Failure) =
        call.respond(HttpStatusCode.InternalServerError, "Błąd bazy: ${result.error.message}")
}

/**
 * Dopisuje utwory i wykonawców z kosza (wykonawca znany już z utworu dostaje tylko `trashed_at`) i odsyła URI bez
 * opisu. Główne zapytanie widzi tabele sprzed wstawienia, więc nowy wpis wychodzi jako brak przez LEFT JOIN, tak
 * samo jak stary bez opisu.
 */
private const val SYNC_SQL = """
WITH incoming_tracks AS (
    SELECT uri FROM UNNEST(@tracks) AS t(uri)
),
     incoming_artists AS (
         SELECT uri FROM UNNEST(@artists) AS a(uri)
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
                      LEFT JOIN spotify.trashed_tracks t ON t.uri = i.uri
             WHERE t.title IS NULL)                                                  AS missing_tracks,
       ARRAY(SELECT i.uri
             FROM incoming_artists i
                      LEFT JOIN spotify.artists a ON a.uri = i.uri
             WHERE a.name IS NULL)                                                   AS missing_artists
"""

private const val UPSERT_ALBUMS = """
INSERT INTO spotify.albums (uri, name)
SELECT uri, name FROM UNNEST(@uris, @names) AS t(uri, name)
ON CONFLICT (uri) DO UPDATE SET name = EXCLUDED.name WHERE albums.name IS DISTINCT FROM EXCLUDED.name
"""

/** Wykonawcy z opisów utworów; nowy nie trafia do kosza, istniejącemu zmienia się tylko nazwa. */
private const val UPSERT_ARTISTS = """
INSERT INTO spotify.artists (uri, name)
SELECT uri, name FROM UNNEST(@uris, @names) AS t(uri, name)
ON CONFLICT (uri) DO UPDATE SET name = EXCLUDED.name WHERE artists.name IS DISTINCT FROM EXCLUDED.name
"""

private const val DESCRIBE_TRACKS = """
UPDATE spotify.trashed_tracks t
SET title = i.title, album_uri = i.album_uri
FROM UNNEST(@uris, @titles, @albums) AS i(uri, title, album_uri)
WHERE t.uri = i.uri
"""

/** Złączenie z koszem pomija utwór wyjęty, zanim opis doszedł. */
private const val INSERT_CREDITS = """
INSERT INTO spotify.track_artists (track_uri, position, artist_uri)
SELECT c.track_uri, c.position, c.artist_uri
FROM UNNEST(@tracks, @positions, @artists) AS c(track_uri, position, artist_uri)
         JOIN spotify.trashed_tracks t ON t.uri = c.track_uri
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

/** Wykonawcy spoza kosza bez żadnego utworu i albumy bez utworów. */
private const val DROP_ORPHANS = """
WITH orphan_artists AS (
    DELETE FROM spotify.artists a
        WHERE a.trashed_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM spotify.track_artists ta WHERE ta.artist_uri = a.uri)
)
DELETE FROM spotify.albums al
WHERE NOT EXISTS (SELECT 1 FROM spotify.trashed_tracks t WHERE t.album_uri = al.uri)
"""
