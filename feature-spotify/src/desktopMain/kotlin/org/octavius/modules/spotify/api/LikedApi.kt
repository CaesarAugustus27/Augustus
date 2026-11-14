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
import org.octavius.modules.spotify.model.LikedSnapshot
import org.octavius.modules.spotify.model.LikedSyncResponse

/**
 * Endpoint dla polubionych. Polubione żyją na serwerze Spotify, więc kopia jest ich lustrem: rozszerzenie przysyła
 * całą listę, a odlajkowane znikają.
 */
class LikedApi : ApiModule, KoinComponent {
    private val db: OctaviusClient by inject()

    override fun installRoutes(routing: Routing) {
        routing.route("/api/spotify/liked") {
            sync()
        }
    }

    /**
     * Definiuje endpoint: POST /api/spotify/liked/sync
     * Przyjmuje całą listę polubionych: dopisuje nowe, poprawia datę polubienia, usuwa odlajkowane i opisuje utwory,
     * które opisu jeszcze nie mają.
     */
    private fun Route.sync() {
        post("/sync") {
            val snapshot = call.receive<LikedSnapshot>()
            val liked = snapshot.tracks.distinctBy { it.uri }
            val uris = liked.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY)

            val result = db.transactionResult {
                rawQuery("INSERT INTO spotify.tracks (uri) SELECT UNNEST(@uris) ON CONFLICT (uri) DO NOTHING").asResult()
                    .executeUpdate("uris" to uris).getOrElse { return@transactionResult it }
                val row = rawQuery(SYNC_LIKED).asResult().fetchRowStrict(
                    "uris" to uris,
                    "liked_at" to liked.map { it.likedAt }.withPgType(PgStandardType.INT8_ARRAY)
                ).getOrElse { return@transactionResult it }
                val described = SpotifyCatalog.describeMissing(snapshot.descriptions).getOrElse { return@transactionResult it }
                SpotifyCatalog.dropOrphans().getOrElse { return@transactionResult it }
                DataResult.Success(LikedSyncResponse(row["added"], row["removed"], described))
            }
            when (result) {
                is DataResult.Success -> call.respond(result.value)
                is DataResult.Failure ->
                    call.respond(HttpStatusCode.InternalServerError, "Błąd bazy: ${result.error.message}")
            }
        }
    }
}

/**
 * Odlajkowane usuwa, nowe dopisuje, a zmienioną datę polubienia (odlajkowany i polubiony z powrotem) poprawia.
 * `xmax = 0` odróżnia wstawiony wiersz od poprawionego.
 */
private const val SYNC_LIKED = """
WITH incoming AS (
    SELECT uri, to_timestamp(liked_at) AS liked_at FROM UNNEST(@uris, @liked_at) AS i(uri, liked_at)
),
     removed AS (
         DELETE FROM spotify.liked_tracks l
             WHERE NOT EXISTS (SELECT 1 FROM incoming i WHERE i.uri = l.uri)
             RETURNING uri
     ),
     upserted AS (
         INSERT INTO spotify.liked_tracks (uri, liked_at)
             SELECT uri, liked_at FROM incoming
             ON CONFLICT (uri) DO UPDATE SET liked_at = EXCLUDED.liked_at
                 WHERE liked_tracks.liked_at IS DISTINCT FROM EXCLUDED.liked_at
             RETURNING xmax = 0 AS inserted
     )
SELECT (SELECT count(*) FROM upserted WHERE inserted)::int AS added,
       (SELECT count(*) FROM removed)::int                 AS removed
"""
