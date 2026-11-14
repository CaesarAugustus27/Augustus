package org.octavius.modules.spotify.spicetify.liked

import kotlinx.coroutines.launch
import org.octavius.modules.spotify.model.Album
import org.octavius.modules.spotify.model.Artist
import org.octavius.modules.spotify.model.LikedSnapshot
import org.octavius.modules.spotify.model.LikedSyncResponse
import org.octavius.modules.spotify.model.LikedTrack
import org.octavius.modules.spotify.model.Track
import org.octavius.modules.spotify.spicetify.AugustusApi
import org.octavius.modules.spotify.spicetify.Spicetify
import org.octavius.modules.spotify.spicetify.spicetifyScope
import org.octavius.modules.spotify.spicetify.textOf
import kotlin.js.json

/**
 * Kopia polubionych w Augustusie. Polubione to w kliencie playlista (`LibraryAPI._likedSongsUri`), a klient danych
 * playlist ją subskrybuje: odpowiedź przychodzi od razu i po każdej zmianie, także z innego urządzenia, z całą listą i
 * opisami utworów. Każdą taką listę wysyłamy w całości, a Augustus robi z niej lustro, więc lista niepełna nie może
 * pójść, bo brakujące wziąłby za odlajkowane.
 */
object LikedCopy {
    private const val TRACK_PREFIX = "spotify:track:"
    private const val ALBUM_PREFIX = "spotify:album:"
    private const val ARTIST_PREFIX = "spotify:artist:"

    /** Zakres listy; dłuższa przyjdzie ucięta i nie pójdzie. */
    private const val RANGE = 100_000

    private var pending: LikedSnapshot? = null
    private var sending = false

    fun start() {
        val library = Spicetify.Platform.LibraryAPI
        library._playlistDataClient.subscribe(request(library._likedSongsUri)) { response -> onUpdate(response?.data) }
    }

    /** Pola, które idą do Augustusa; `showUnavailable`, bo bez niego klient pomija utwory, których już nie odtworzy. */
    private fun request(uri: String): dynamic = json(
        "uri" to uri,
        "policy" to json(
            "playlist" to json("unfilteredLength" to true),
            "item" to json("addTime" to true),
            "track" to json(
                "track" to json("link" to true, "name" to true),
                "album" to json("album" to json("link" to true, "name" to true)),
                "artist" to json("link" to true, "name" to true)
            )
        ),
        "query" to json(
            "range" to json("start" to 0, "length" to RANGE),
            "showUnavailable" to true,
            "updateThrottlingMs" to 1000
        )
    )

    private fun onUpdate(data: dynamic) {
        if (data == null || data.loadingContents == true) return
        val items = data.item as? Array<dynamic> ?: return
        val length = (data.unfilteredLength as? Number)?.toInt() ?: return
        if (items.size != length) {
            console.warn("Augustus: lista polubionych przyszła niepełna (${items.size} z $length), nie idzie do aplikacji")
            return
        }
        pending = snapshot(items)
        send()
    }

    private fun snapshot(items: Array<dynamic>): LikedSnapshot {
        val liked = mutableListOf<LikedTrack>()
        val descriptions = mutableListOf<Track>()
        for (item in items) {
            val metadata: dynamic = item.trackMetadata ?: continue
            val uri = textOf(metadata.link) ?: continue
            // Pliki lokalne (spotify:local:…) kosz i kopia pomijają
            if (!uri.startsWith(TRACK_PREFIX) || item.addTime == null) continue
            // addTime to sekundy, zależnie od zapytania liczba albo BigInt
            liked.add(LikedTrack(uri, (js("Number")(item.addTime) as Number).toLong()))
            describe(uri, metadata)?.let { descriptions.add(it) }
        }
        return LikedSnapshot(liked, descriptions)
    }

    private fun describe(uri: String, metadata: dynamic): Track? {
        val title = textOf(metadata.name) ?: return null
        val albumUri = textOf(metadata.album?.link)?.takeIf { it.startsWith(ALBUM_PREFIX) }
        val albumName = textOf(metadata.album?.name)
        val album = if (albumUri != null && albumName != null) Album(albumUri, albumName) else null
        val artists = (metadata.artist as? Array<dynamic> ?: emptyArray()).mapNotNull { artist ->
            val artistUri = textOf(artist?.link)?.takeIf { it.startsWith(ARTIST_PREFIX) }
            val name = textOf(artist?.name)
            if (artistUri != null && name != null) Artist(artistUri, name) else null
        }
        return Track(uri, title, album, artists)
    }

    /** Jedna wysyłka naraz; lista, która przyjdzie w trakcie, idzie zaraz po bieżącej, a starsze przepadają. */
    private fun send() {
        if (sending) return
        sending = true
        spicetifyScope.launch {
            try {
                while (true) {
                    val snapshot = pending ?: break
                    pending = null
                    val result: LikedSyncResponse = AugustusApi.post("/liked/sync", snapshot)
                    if (result.added + result.removed + result.described > 0) {
                        console.log("Augustus: polubione w kopii: +${result.added}, -${result.removed}, opisane: ${result.described}")
                    }
                }
            } catch (e: Throwable) {
                console.warn("Augustus: aplikacja nie odpowiada, polubione dogonią przy następnej zmianie albo starcie", e)
            } finally {
                sending = false
            }
        }
    }
}
