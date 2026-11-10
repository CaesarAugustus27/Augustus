package org.octavius.modules.spotify.model

import kotlinx.serialization.Serializable

/** Zawartość kosza tak, jak trzyma ją LocalStorage Trashbina: same URI utworów i wykonawców. */
@Serializable
data class TrashUris(
    val tracks: List<String>,
    val artists: List<String>
)

/** Ile wpisów synchronizacja dopisała i które z przysłanych URI baza musi jeszcze dostać opisane. */
@Serializable
data class TrashSyncResponse(
    val added: Int,
    val missingTracks: List<String>,
    val missingArtists: List<String>
)

@Serializable
data class Artist(
    val uri: String,
    val name: String
)

@Serializable
data class Album(
    val uri: String,
    val name: String
)

/** Opis utworu z odpowiedzi Spotify; [artists] w kolejności ze Spotify. */
@Serializable
data class Track(
    val uri: String,
    val title: String,
    val album: Album?,
    val artists: List<Artist>
)

/** Opisy dosyłane przez Trashbina: utwory z kosza i nazwy wykonawców wrzuconych do kosza. */
@Serializable
data class TrashDescriptions(
    val tracks: List<Track>,
    val artists: List<Artist>
)

@Serializable
data class TrashDescribeResponse(
    val described: Int
)

/** Wpisy wyjęte z kosza: URI utworów i wykonawców razem, bo i tak się nie powtarzają. */
@Serializable
data class TrashRemoveRequest(
    val uris: List<String>
)

@Serializable
data class TrashRemoveResponse(
    val removed: Int
)
