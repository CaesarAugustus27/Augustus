package org.octavius.modules.spotify.trashbin

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.octavius.modules.spotify.model.Album
import org.octavius.modules.spotify.model.Artist
import org.octavius.modules.spotify.model.Track
import org.octavius.modules.spotify.model.TrashDescribeResponse
import org.octavius.modules.spotify.model.TrashDescriptions
import org.octavius.modules.spotify.model.TrashRemoveRequest
import org.octavius.modules.spotify.model.TrashRemoveResponse
import org.octavius.modules.spotify.model.TrashSyncResponse
import org.octavius.modules.spotify.model.TrashUris
import org.w3c.fetch.RequestInit
import kotlin.js.json

/**
 * Kopia kosza w Augustusie (aplikacja na 127.0.0.1:8080, schemat `spotify`).
 *
 * Źródłem prawdy jest LocalStorage: kosz musi działać od razu, także gdy aplikacja nie jest uruchomiona. Augustus
 * dostaje URI całego kosza po każdym zapisie i przy starcie, a sam tylko dopisuje, więc LocalStorage wyczyszczony na
 * przykład reinstalacją Spotify nie wyczyści kopii. Kasuje w niej wyłącznie wyjęcie z kosza; takie, które nie doszło,
 * czeka w LocalStorage i idzie przed następną synchronizacją.
 *
 * Opisy (tytuł, album, wykonawcy, nazwa wykonawcy z kosza) dosyłamy do URI, których Augustus jeszcze nie opisał.
 * Pobieramy je tymi samymi zapytaniami GraphQL, których używa sam Spotify: `decorateContextTracks` (lista URI utworów)
 * i `getArtistNameAndTracks` (URI wykonawcy).
 */
object AugustusCopy {
    private const val URL = "http://127.0.0.1:8080/api/spotify/trash"
    private const val PENDING_REMOVALS_KEY = "TrashbinAugustusRemovals"
    private const val DESCRIBE_BATCH = 50

    /** Przerwa między zapytaniami, żeby nie zasypać Spotify, kiedy do opisania jest cały kosz. */
    private const val DESCRIBE_PAUSE_MS = 200L

    /** URI, których Spotify nie zna (np. utwór usunięty z serwisu); do restartu nie pytamy o nie ponownie. */
    private val unavailable = mutableSetOf<String>()
    private val pendingRemovals: MutableList<String> = readPendingRemovals()
    private var syncing = false
    private var syncAgain = false

    /** Jedna synchronizacja naraz; zmiana w trakcie dokłada jeszcze jeden przebieg po bieżącym. */
    fun sync() {
        if (syncing) {
            syncAgain = true
            return
        }
        syncing = true
        trashbinScope.launch {
            try {
                do {
                    syncAgain = false
                    flushRemovals()
                    val result: TrashSyncResponse =
                        post("/sync", TrashUris(TrashStore.trackUris.toList(), TrashStore.artistUris.toList()))
                    if (result.added > 0) console.log("Trashbin: nowe pozycje w Augustusie: ${result.added}")
                    describe(result.missingTracks, result.missingArtists)
                } while (syncAgain)
            } catch (e: Throwable) {
                console.warn("Trashbin: Augustus nie odpowiada, kopia dogoni kosz przy następnej zmianie albo starcie", e)
            } finally {
                syncing = false
            }
        }
    }

    /** Dokłada z Augustusa to, czego brakuje w LocalStorage, i pomija wyjęte, które jeszcze tam nie doszły. */
    suspend fun restore() {
        try {
            val response = window.fetch(URL).await()
            val text = response.text().await()
            if (!response.ok) throw IllegalStateException("${response.status} $text")
            val restored = TrashStore.restore(Json.decodeFromString<TrashUris>(text), pendingRemovals.toList())
            Spicetify.showNotification("Przywrócone z Augustusa: $restored")
        } catch (e: Throwable) {
            Spicetify.showNotification("Nie udało się przywrócić - czy Augustus jest uruchomiony?", true)
            console.error(e)
        }
    }

    fun queueRemoval(uri: String) {
        if (uri !in pendingRemovals) pendingRemovals.add(uri)
        savePendingRemovals()
    }

    /** Wrzucony z powrotem, zanim wyjęcie doszło do Augustusa; wyjęcie jest już nieaktualne. */
    fun cancelRemoval(uri: String) {
        if (pendingRemovals.remove(uri)) savePendingRemovals()
    }

    private suspend fun flushRemovals() {
        if (pendingRemovals.isEmpty()) return
        val uris = pendingRemovals.toList()
        post<TrashRemoveRequest, TrashRemoveResponse>("/remove", TrashRemoveRequest(uris))
        // W trakcie mogły dojść kolejne, więc zdejmujemy tylko wysłane
        pendingRemovals.removeAll(uris)
        savePendingRemovals()
    }

    private fun readPendingRemovals(): MutableList<String> {
        val stored = ClientStorage.read(PENDING_REMOVALS_KEY) as? JsonArray ?: return mutableListOf()
        return stored.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toMutableList()
    }

    private fun savePendingRemovals() {
        try {
            ClientStorage.write(PENDING_REMOVALS_KEY, JsonArray(pendingRemovals.map { JsonPrimitive(it) }))
        } catch (e: Throwable) {
            console.error(e)
        }
    }

    /**
     * Paczkami po [DESCRIBE_BATCH], każda zapisana od razu: przerwany przebieg (przy pustej kopii to cały kosz) idzie
     * przy następnym od miejsca, w którym stanął.
     */
    private suspend fun describe(missingTracks: List<String>, missingArtists: List<String>) {
        val graphQL = Spicetify.GraphQL
        val decorateContextTracks = graphQL?.Definitions?.decorateContextTracks
        val getArtistNameAndTracks = graphQL?.Definitions?.getArtistNameAndTracks
        if (graphQL == null || graphQL.asDynamic().Request == null || decorateContextTracks == null || getArtistNameAndTracks == null) {
            console.warn("Trashbin: brak zapytań GraphQL, Augustus nie dostanie nazw")
            return
        }

        val tracks = missingTracks.filter { it !in unavailable }
        val artists = missingArtists.filter { it !in unavailable }
        val long = tracks.size + artists.size >= DESCRIBE_BATCH
        if (long) Spicetify.showNotification("Trashbin: do opisania dla Augustusa: ${tracks.size + artists.size}…")
        var described = 0

        for (batch in tracks.chunked(DESCRIBE_BATCH)) {
            val response: dynamic = graphQL.Request(decorateContextTracks, json("uris" to batch.toTypedArray())).await()
            val found = (response?.data ?: response)?.tracks as? Array<dynamic>
            if (found.isNullOrEmpty()) throw IllegalStateException(JSON.stringify(response?.errors ?: response))
            val descriptions = mutableListOf<Track>()
            batch.forEachIndexed { i, uri ->
                val track: dynamic = if (found.size == batch.size) found[i] else found.firstOrNull { it?.uri == uri }
                val description = if (track?.__typename == "Track") describeTrack(uri, track) else null
                if (description != null) descriptions.add(description) else unavailable.add(uri)
            }
            if (descriptions.isNotEmpty()) {
                val response: TrashDescribeResponse = post("/describe", TrashDescriptions(descriptions, emptyList()))
                described += response.described
            }
            delay(DESCRIBE_PAUSE_MS)
        }

        for (uri in artists) {
            val response: dynamic =
                graphQL.Request(getArtistNameAndTracks, json("uri" to uri, "offset" to 0, "limit" to 1)).await()
            val artistUnion: dynamic = (response?.data ?: response)?.artistUnion
            if (artistUnion == null) throw IllegalStateException(JSON.stringify(response?.errors ?: response))
            val name = text(artistUnion.profile?.name)
            if (name == null) {
                unavailable.add(uri)
            } else {
                val saved: TrashDescribeResponse = post("/describe", TrashDescriptions(emptyList(), listOf(Artist(uri, name))))
                described += saved.described
            }
            delay(DESCRIBE_PAUSE_MS)
        }

        if (long) Spicetify.showNotification("Trashbin: opisy wysłane do Augustusa: $described")
    }

    /** Kształt odpowiedzi taki, jak mapuje go sam klient Spotify (`artists.items[].uri` + `profile.name`, `albumOfTrack`). */
    private fun describeTrack(uri: String, track: dynamic): Track? {
        val title = text(track.name) ?: return null
        val items = track.artists?.items as? Array<dynamic> ?: emptyArray()
        val artists = items.mapNotNull { artist ->
            val artistUri = text(artist?.uri)
            val name = text(artist?.profile?.name)
            if (artistUri != null && name != null) Artist(artistUri, name) else null
        }
        val albumUri = text(track.albumOfTrack?.uri)
        val albumName = text(track.albumOfTrack?.name)
        val album = if (albumUri != null && albumName != null) Album(albumUri, albumName) else null
        return Track(uri, title, album, artists)
    }

    /** Niepusty napis albo `null`. */
    private fun text(value: dynamic): String? = (value as? String)?.takeIf { it.isNotEmpty() }

    private suspend inline fun <reified Request, reified Response> post(path: String, body: Request): Response {
        val init = RequestInit(
            method = "POST",
            headers = json("Content-Type" to "application/json"),
            body = Json.encodeToString(body)
        )
        val response = window.fetch("$URL$path", init).await()
        val text = response.text().await()
        if (!response.ok) throw IllegalStateException("${response.status} $text")
        return Json.decodeFromString(text)
    }
}
