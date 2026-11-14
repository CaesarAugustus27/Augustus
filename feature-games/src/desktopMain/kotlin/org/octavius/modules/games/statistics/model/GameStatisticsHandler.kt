package org.octavius.modules.games.statistics.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager

class GameStatisticsHandler(val scope: CoroutineScope) : KoinComponent {
    private val db: OctaviusClient by inject()
    private val _state = MutableStateFlow(GameStatisticsState())
    val state = _state.asStateFlow()

    init {
        loadData()
    }

    private fun buildGameStatisticsQuery(): String {
        // --- CTE 1: kpi_stats ---
        val kpiStatsQuery = db.select(
            "COUNT(*) AS total_games",
            "(SELECT time_played FROM games.time_played) AS total_playtime_hours",
            "COUNT(*) FILTER (WHERE g.status = 'PLAYED') AS played_games_count",
            "COALESCE(AVG(pt.play_time_hours) FILTER (WHERE g.status = 'PLAYED'), 0)::numeric(10, 2) AS avg_playtime_for_played"
        )
            .from("games.games g LEFT JOIN games.play_time pt ON g.id = pt.game_id")
            .toSql()

        // --- CTE 2: status_distribution ---
        val statusDistributionQuery = db.select(
            """
            array_agg(ROW('status', status, 'count', count) ORDER BY count DESC) AS distribution
        """
        )
            .fromSubquery(
                db.select("status, COUNT(*) as count")
                    .from("games.games")
                    .groupBy("status")
                    .toSql()
            )
            .toSql()

        // --- CTE 3: rating_stats ---
        // Bez zmian.
        val ratingStatsQuery = db.select(
            "AVG(story_rating)::numeric(10, 2) AS avg_story_rating",
            "AVG(gameplay_rating)::numeric(10, 2) AS avg_gameplay_rating",
            "AVG(atmosphere_rating)::numeric(10, 2) AS avg_atmosphere_rating"
        )
            .from("games.ratings")
            .toSql()

        // --- CTE 4: most_played_games ---
        val mostPlayedGamesQuery = db.select(
            """
            array_agg(
                ROW('id', g.id, 'name', g.name, 'play_time_hours', pt.play_time_hours)
                ORDER BY pt.play_time_hours DESC
            ) AS games
        """
        )
            .from("games.play_time pt JOIN games.games g ON pt.game_id = g.id")
            .where(
                "pt.game_id IN (${
                    db.select("game_id").from("games.play_time").orderBy("play_time_hours DESC NULLS LAST")
                        .limit(5).toSql()
                })"
            )
            .toSql()

        // --- CTE 5: highest_rated_games ---
        val avgRatingCalculation = """
        (SELECT avg(v) FROM (VALUES (story_rating), (gameplay_rating), (atmosphere_rating)) t(v))::numeric(10, 2)
    """.trimIndent()
        val ratingCount = "num_nonnulls(story_rating, gameplay_rating, atmosphere_rating)"

        // Przy równej średniej wyżej gra oceniona w większej liczbie kategorii, potem alfabetycznie
        val highestRatedGamesQuery = db.select(
            """
            array_agg(
                ROW('id', r.id, 'name', r.name, 'average_rating', r.avg_rating)
                ORDER BY r.avg_rating DESC, r.rating_count DESC, r.name
            ) AS games
        """
        )
            .from(
                "(${
                    db.select("g.id, g.name, $avgRatingCalculation AS avg_rating, $ratingCount AS rating_count")
                        .from("games.ratings rt JOIN games.games g ON rt.game_id = g.id")
                        .where("$ratingCount > 0") // Wyrzucamy gry bez ocen od razu
                        .orderBy("avg_rating DESC, rating_count DESC, name")
                        .limit(5)
                        .toSql()
                }) r"
            )
            .toSql()

        // --- CTE 6: favorite_category ---
        val favoriteCategoryQuery = "SELECT (${
            db.select("c.name")
                .from("games.categories_to_games ctg JOIN games.play_time pt ON ctg.game_id = pt.game_id JOIN games.categories c ON ctg.category_id = c.id")
                .groupBy("c.id, c.name")
                .orderBy("SUM(pt.play_time_hours) DESC")
                .limit(1)
                .toSql()
        }) AS name"

        // --- CTE 7: favorite_series ---
        val favoriteSeriesQuery = "SELECT (${
            db.select("s.name")
                .from("games.games g JOIN games.series s ON g.series = s.id JOIN games.play_time pt ON g.id = pt.game_id")
                .groupBy("s.id, s.name")
                .orderBy("SUM(pt.play_time_hours) DESC")
                .limit(1)
                .toSql()
        }) AS name"

        return db.select(
            """
            kpi.total_games,
            kpi.total_playtime_hours,
            kpi.played_games_count,
            kpi.avg_playtime_for_played,
            sd.distribution AS status_distribution,
            rs.avg_story_rating,
            rs.avg_gameplay_rating,
            rs.avg_atmosphere_rating,
            mpg.games AS most_played_games,
            hrg.games AS highest_rated_games,
            fc.name AS favorite_category_name,
            fs.name AS favorite_series_name
            """
        )
            .with("kpi_stats", kpiStatsQuery)
            .with("status_distribution", statusDistributionQuery)
            .with("rating_stats", ratingStatsQuery)
            .with("most_played_games", mostPlayedGamesQuery)
            .with("highest_rated_games", highestRatedGamesQuery)
            .with("favorite_category", favoriteCategoryQuery)
            .with("favorite_series", favoriteSeriesQuery)
            .from("kpi_stats kpi, status_distribution sd, rating_stats rs, most_played_games mpg, highest_rated_games hrg, favorite_category fc, favorite_series fs")
            .toSql()
    }

    fun loadData() {
        scope.launch {
            val query = buildGameStatisticsQuery()
            val result = withContext(OctaviusDispatchers.Virtual) {
                db.rawQuery(query).asResult().fetchObjectStrict<GameStatisticsData>()
            }
            when (result) {
                is DataResult.Success -> {
                    _state.update {
                        it.copy(
                            data = result.value,
                            isLoading = false
                        )
                    }
                }

                is DataResult.Failure -> {
                    showError(result.error)
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun showError(error: OctaviusException) {
        GlobalDialogManager.show(ErrorDialogConfig(error))
    }
}