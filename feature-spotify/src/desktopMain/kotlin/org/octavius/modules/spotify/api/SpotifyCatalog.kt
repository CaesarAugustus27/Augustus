package org.octavius.modules.spotify.api

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.getOrElse
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.octavius.modules.spotify.model.Track

/**
 * Opisy utworów wspólne dla kosza i polubionych: `tracks`, `albums`, wykonawcy w `artists` i `track_artists`.
 * Wołane w transakcji dołączają do niej.
 */
object SpotifyCatalog : KoinComponent {
    private val db: OctaviusClient by inject()

    /** Zapisuje opisy utworów, które są już w `tracks`, i zwraca, ile ich opisał. Opis utworu spoza tabeli przepada. */
    fun describe(tracks: List<Track>): DataResult<Int> {
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
            DataResult.Success(described.toInt())
        }
    }

    /** [describe] tylko dla utworów, które w `tracks` nie mają jeszcze opisu. */
    fun describeMissing(tracks: List<Track>): DataResult<Int> = db.transactionResult {
        val missing = rawQuery("SELECT uri FROM spotify.tracks WHERE uri = ANY(@uris) AND title IS NULL").asResult()
            .fetchFields<String>("uris" to tracks.map { it.uri }.withPgType(PgStandardType.TEXT_ARRAY))
            .getOrElse { return@transactionResult it }
            .toSet()
        describe(tracks.filter { it.uri in missing })
    }

    /** Usuwa utwory spoza kosza i polubionych, potem wykonawców spoza kosza bez utworów i albumy bez utworów. */
    fun dropOrphans(): DataResult<Unit> = db.transactionResult {
        // Osobno, bo zapytanie z wykonawcami widziałoby jeszcze track_artists usuwanych utworów
        rawQuery(DROP_ORPHAN_TRACKS).asResult().executeUpdate().getOrElse { return@transactionResult it }
        rawQuery(DROP_ORPHAN_ARTISTS_AND_ALBUMS).asResult().executeUpdate().getOrElse { return@transactionResult it }
        DataResult.Success(Unit)
    }
}

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
UPDATE spotify.tracks t
SET title = i.title, album_uri = i.album_uri
FROM UNNEST(@uris, @titles, @albums) AS i(uri, title, album_uri)
WHERE t.uri = i.uri
"""

/** Złączenie z `tracks` pomija utwór, który zniknął, zanim opis doszedł. */
private const val INSERT_CREDITS = """
INSERT INTO spotify.track_artists (track_uri, position, artist_uri)
SELECT c.track_uri, c.position, c.artist_uri
FROM UNNEST(@tracks, @positions, @artists) AS c(track_uri, position, artist_uri)
         JOIN spotify.tracks t ON t.uri = c.track_uri
"""

/** Wykonawców utworu sprząta ON DELETE CASCADE w `track_artists`. */
private const val DROP_ORPHAN_TRACKS = """
DELETE FROM spotify.tracks t
WHERE NOT EXISTS (SELECT 1 FROM spotify.trashed_tracks tt WHERE tt.uri = t.uri)
  AND NOT EXISTS (SELECT 1 FROM spotify.liked_tracks l WHERE l.uri = t.uri)
"""

private const val DROP_ORPHAN_ARTISTS_AND_ALBUMS = """
WITH orphan_artists AS (
    DELETE FROM spotify.artists a
        WHERE a.trashed_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM spotify.track_artists ta WHERE ta.artist_uri = a.uri)
)
DELETE FROM spotify.albums al
WHERE NOT EXISTS (SELECT 1 FROM spotify.tracks t WHERE t.album_uri = al.uri)
"""
