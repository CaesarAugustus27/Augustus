package org.octavius.modules.spotify.spicetify.trash

import kotlinx.browser.document
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.octavius.modules.spotify.model.TrashUris
import org.octavius.modules.spotify.spicetify.ClientStorage
import org.octavius.modules.spotify.spicetify.PlayerTrack
import org.octavius.modules.spotify.spicetify.Spicetify
import org.octavius.modules.spotify.spicetify.artistUris
import org.w3c.dom.CustomEvent

/**
 * Kosz w LocalStorage klienta: same URI utworów i wykonawców, pod kluczami i w formacie Trashbina ze Spicetify
 * (URI → `true`). Tytuły, albumy i nazwy wykonawców są w Augustusie.
 */
object TrashStore {
    private const val TRACKS_KEY = "TrashSongList"
    private const val ARTISTS_KEY = "TrashArtistList"
    private const val TRACK_PREFIX = "spotify:track:"
    private const val ARTIST_PREFIX = "spotify:artist:"

    /** Zdarzenie na `document` po każdej zmianie kosza. */
    const val CHANGE_EVENT = "trashbinChange"

    private val tracks: MutableSet<String> = read(TRACKS_KEY)
    private val artists: MutableSet<String> = read(ARTISTS_KEY)

    val trackUris: Set<String> get() = tracks
    val artistUris: Set<String> get() = artists

    private fun read(key: String): MutableSet<String> {
        val stored = ClientStorage.read(key) as? JsonObject ?: return LinkedHashSet()
        return stored.filterValues { it == JsonPrimitive(true) }.keys.toCollection(LinkedHashSet())
    }

    /** Czy kosz przyjmuje [uri]; poza utworem i wykonawcą (album, odcinek, plik lokalny) nie. */
    fun accepts(uri: String): Boolean = setFor(uri) != null

    operator fun contains(uri: String): Boolean = setFor(uri)?.contains(uri) == true

    /** Utwór w koszu albo z wykonawcą z kosza. */
    fun isTrashed(track: PlayerTrack): Boolean = track.uri in tracks || track.artistUris().any { it in artists }

    private fun setFor(uri: String): MutableSet<String>? = when {
        uri.startsWith(TRACK_PREFIX) -> tracks
        uri.startsWith(ARTIST_PREFIX) -> artists
        else -> null
    }

    /** Wrzuca do kosza albo wyjmuje; wrzucony grający utwór od razu przeskakuje. */
    fun toggle(uri: String) {
        val set = setFor(uri) ?: return
        val what = if (set === tracks) "Utwór" else "Wykonawca"

        if (set.remove(uri)) {
            TrashCopy.queueRemoval(uri)
            Spicetify.showNotification("$what wyjęty z kosza")
        } else {
            set.add(uri)
            TrashCopy.cancelRemoval(uri)
            Spicetify.showNotification("$what w koszu, będzie pomijany")
        }
        store()
        if (uri in set) Skipper.skipIfPlaying(uri)
    }

    /** Dokłada wpisy z [copy] poza tymi z [skip] i zwraca, ile ich przybyło. */
    fun restore(copy: TrashUris, skip: Collection<String>): Int {
        var restored = 0
        for ((set, uris) in listOf(tracks to copy.tracks, artists to copy.artists)) {
            for (uri in uris) {
                if (uri !in skip && set.add(uri)) restored++
            }
        }
        store()
        return restored
    }

    /** Zapisuje kosz, odświeża przyciski i wysyła kosz do Augustusa. */
    private fun store() {
        try {
            ClientStorage.write(TRACKS_KEY, asTrashbinMap(tracks))
            ClientStorage.write(ARTISTS_KEY, asTrashbinMap(artists))
        } catch (e: Throwable) {
            Spicetify.showNotification("Nie udało się zapisać kosza w LocalStorage", true)
            console.error(e)
        }
        document.dispatchEvent(CustomEvent(CHANGE_EVENT))
        TrashCopy.sync()
    }

    private fun asTrashbinMap(uris: Set<String>) = JsonObject(uris.associateWith { JsonPrimitive(true) })
}
