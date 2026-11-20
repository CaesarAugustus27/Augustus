package org.octavius.modules.spotify.statistics.model

import kotlinx.datetime.LocalDate

/** Obecnie polubione utwory dodane w danym roku. */
data class LikedInYear(
    val year: Int,
    val count: Long
)

/** Utwory, które trafiły do kosza w tygodniu zaczynającym się w poniedziałek [weekStart]. */
data class TrashedInWeek(
    val weekStart: LocalDate,
    val count: Long
)

/** Album z liczbą utworów w koszu i polubionych; [artist] to najczęstszy pierwszy wykonawca jego utworów. */
data class AlbumCounts(
    val name: String,
    val artist: String?,
    val trashed: Long,
    val liked: Long
)

data class SpotifyStatisticsData(
    val trashedCount: Long,
    val likedCount: Long,
    val artistCount: Long,
    val albumCount: Long,
    val trashedSinceCopy: Long,
    val likedByYear: List<LikedInYear>,
    val trashedByWeek: List<TrashedInWeek>,
    val topTrashedAlbums: List<AlbumCounts>
)
