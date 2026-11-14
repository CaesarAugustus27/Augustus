package org.octavius.modules.games.statistics.model

import org.octavius.domain.game.GameStatus
import java.math.BigDecimal


interface DashboardGame {
    val id: Int
    val name: String
    val value: String
}

data class DashboardStatusCount(
    val status: GameStatus,
    val count: Long
)

// Typ dla gry w liście "najwięcej grane"
data class DashboardGameByTime(
    override val id: Int,
    override val name: String,
    val playTimeHours: BigDecimal
) : DashboardGame {
    override val value: String = "${this.playTimeHours}h"
}

// Typ dla gry w liście "najwyżej oceniane"
data class DashboardGameByRating(
    override val id: Int,
    override val name: String,
    val averageRating: BigDecimal
) : DashboardGame {
    override val value: String = averageRating.toString()
}

// Główna data class, która mapuje się na wynik całego zapytania
data class GameStatisticsData(
    val totalGames: Long,
    val totalPlaytimeHours: BigDecimal,
    val playedGamesCount: Long,
    val avgPlaytimeForPlayed: BigDecimal,
    val statusDistribution: List<DashboardStatusCount>?,
    val avgStoryRating: BigDecimal?,
    val avgGameplayRating: BigDecimal?,
    val avgAtmosphereRating: BigDecimal?,
    val mostPlayedGames: List<DashboardGameByTime>?,
    val highestRatedGames: List<DashboardGameByRating>?,
    val favoriteCategoryName: String?,
    val favoriteSeriesName: String?
)

// Stan, którego będzie używać UI
data class GameStatisticsState(
    val data: GameStatisticsData? = null,
    val isLoading: Boolean = true
)