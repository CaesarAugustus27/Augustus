package org.octavius.modules.games.statistics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.octavius.domain.game.GameStatus
import org.octavius.error.rememberSupervisedCoroutineScope
import org.octavius.localization.Tr
import org.octavius.modules.games.form.game.ui.GameFormScreen
import org.octavius.modules.games.localization.GamesTr
import org.octavius.modules.games.statistics.model.DashboardGame
import org.octavius.modules.games.statistics.model.GameStatisticsData
import org.octavius.modules.games.statistics.model.GameStatisticsHandler
import org.octavius.navigation.AppRouter
import org.octavius.navigation.Screen

class GameStatisticsScreen(override val title: String) : Screen {
    @Composable
    override fun Content() {
        val composableScope = rememberSupervisedCoroutineScope()

        val handler = remember(composableScope) {
            GameStatisticsHandler(composableScope)
        }
        val statistics by handler.data.collectAsState()

        val data = statistics
        if (data == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { KpiStats(data) }
                item { RatingStats(data) }
                item { FavoriteStats(data) }

                item {
                    QuickAccessGameList(
                        title = GamesTr.Stats.mostPlayed(),
                        items = data.mostPlayedGames.orEmpty(),
                        onItemClick = { AppRouter.navigateTo(GameFormScreen.create(it)) }
                    )
                }

                item {
                    QuickAccessGameList(
                        title = GamesTr.Stats.highestRated(),
                        items = data.highestRatedGames.orEmpty(),
                        onItemClick = { AppRouter.navigateTo(GameFormScreen.create(it)) }
                    )
                }
            }
        }
    }

    companion object {
        fun create(): Screen {
            return GameStatisticsScreen(GamesTr.Stats.title())
        }
    }
}


private val statusCardOrder = listOf(
    GameStatus.Playing,
    GameStatus.ToPlay,
    GameStatus.Played,
    GameStatus.WithoutTheEnd,
    GameStatus.Trash
)

@Composable
private fun KpiStats(data: GameStatisticsData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(GamesTr.Stats.kpiHeader(), style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(GamesTr.Stats.totalGames(), data.totalGames.toString(), Modifier.weight(1f))
            statusCardOrder.forEach { status ->
                StatCard(status.toDisplayString(), data.statusCount(status).toString(), Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                GamesTr.Stats.totalPlaytime(),
                "${data.totalPlaytimeHours}h",
                Modifier.weight(1f)
            )
            StatCard(
                GamesTr.Stats.avgPlaytime(),
                "${data.avgPlaytimeForPlayed}h",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RatingStats(data: GameStatisticsData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(GamesTr.Stats.ratingHeader(), style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(GamesTr.Stats.avgStory(), data.avgStoryRating?.toString() ?: "-", Modifier.weight(1f))
            StatCard(GamesTr.Stats.avgGameplay(), data.avgGameplayRating?.toString() ?: "-", Modifier.weight(1f))
            StatCard(GamesTr.Stats.avgAtmosphere(), data.avgAtmosphereRating?.toString() ?: "-", Modifier.weight(1f))
        }
    }
}

@Composable
private fun FavoriteStats(data: GameStatisticsData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(GamesTr.Stats.favoritesHeader(), style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(GamesTr.Stats.favoriteCategory(), data.favoriteCategoryName ?: "-", Modifier.weight(1f))
            StatCard(GamesTr.Stats.favoriteSeries(), data.favoriteSeriesName ?: "-", Modifier.weight(1f))
        }
    }
}


@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun QuickAccessGameList(
    title: String,
    items: List<DashboardGame>,
    onItemClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (items.isEmpty()) {
            Text(Tr.Statistics.noItemsInList(), style = MaterialTheme.typography.bodyMedium)
        } else {
            Card {
                Column {
                    items.forEachIndexed { index, item ->

                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text(item.value) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable { onItemClick(item.id) }
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}