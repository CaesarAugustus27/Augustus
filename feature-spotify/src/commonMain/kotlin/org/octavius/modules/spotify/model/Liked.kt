package org.octavius.modules.spotify.model

import kotlinx.serialization.Serializable

/** Polubiony utwór; [likedAt] to data polubienia ze Spotify w sekundach od 1970-01-01 UTC. */
@Serializable
data class LikedTrack(
    val uri: String,
    val likedAt: Long
)

/** Cała lista „Polubione utwory” ze Spotify z opisami utworów, które Spotify jeszcze zna. */
@Serializable
data class LikedSnapshot(
    val tracks: List<LikedTrack>,
    val descriptions: List<Track>
)

/** Ile polubionych przybyło, ile zniknęło i ile utworów dostało opis. */
@Serializable
data class LikedSyncResponse(
    val added: Int,
    val removed: Int,
    val described: Int
)
