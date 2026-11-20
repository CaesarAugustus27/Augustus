package org.octavius.modules.spotify.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.octavius.error.rememberSupervisedCoroutineScope
import org.octavius.localization.Tr
import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.modules.spotify.report.ui.ArtistsReportScreen
import org.octavius.modules.spotify.statistics.model.AlbumCounts
import org.octavius.modules.spotify.statistics.model.SpotifyStatisticsData
import org.octavius.modules.spotify.statistics.model.SpotifyStatisticsHandler
import org.octavius.modules.spotify.statistics.model.TRASH_COPY_STARTED_AT
import org.octavius.navigation.AppRouter
import org.octavius.navigation.Screen
import org.octavius.util.DateAdapter

/** Ekran główny zakładki Spotify: liczby, polubione według roku, kosz od początku kopii i albumy z największym koszem. */
class SpotifyStatisticsScreen(override val title: String) : Screen {

    @Composable
    override fun Content() {
        val composableScope = rememberSupervisedCoroutineScope()
        val handler = remember(composableScope) { SpotifyStatisticsHandler(composableScope) }
        val data by handler.data.collectAsState()

        LaunchedEffect(Unit) {
            handler.loadData()
        }

        when (val loaded = data) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> Statistics(loaded)
        }
    }

    companion object {
        fun create(): Screen = SpotifyStatisticsScreen(SpotifyTr.Stats.title())
    }
}

@Composable
private fun Statistics(data: SpotifyStatisticsData) {
    val copyStarted = DateAdapter.format(TRASH_COPY_STARTED_AT.toLocalDateTime(TimeZone.currentSystemDefault()).date)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(SpotifyTr.Stats.trashed(), data.trashedCount.toString(), Modifier.weight(1f))
                StatCard(SpotifyTr.Stats.trashedSince(date = copyStarted), data.trashedSinceCopy.toString(), Modifier.weight(1f))
                StatCard(SpotifyTr.Stats.liked(), data.likedCount.toString(), Modifier.weight(1f))
                StatCard(SpotifyTr.Stats.artists(), data.artistCount.toString(), Modifier.weight(1f))
                StatCard(SpotifyTr.Stats.albums(), data.albumCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            OutlinedButton(
                onClick = { AppRouter.navigateTo(ArtistsReportScreen.create()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(SpotifyTr.Stats.artistsReport())
            }
        }

        item {
            Section(SpotifyTr.Stats.likedByYear(), SpotifyTr.Stats.likedByYearNote()) {
                CountBars(data.likedByYear.map { it.year.toString() to it.count })
            }
        }

        item {
            Section(SpotifyTr.Stats.trashByWeek(date = copyStarted), SpotifyTr.Stats.trashByWeekNote()) {
                CountBars(data.trashedByWeek.map { DateAdapter.format(it.weekStart) to it.count })
            }
        }

        item {
            Section(SpotifyTr.Stats.topTrashedAlbums()) {
                AlbumList(data.topTrashedAlbums)
            }
        }
    }
}

@Composable
private fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Poziome słupki: etykieta, słupek względem największej wartości, liczba. */
@Composable
private fun CountBars(bars: List<Pair<String, Long>>) {
    if (bars.isEmpty()) {
        Text(Tr.Statistics.noItemsInList(), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val max = bars.maxOf { it.second }.coerceAtLeast(1)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEach { (label, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).height(16.dp)) {
                        if (count > 0) {
                            Box(
                                Modifier.fillMaxHeight()
                                    .fillMaxWidth(count.toFloat() / max)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    Text(
                        count.toString(),
                        Modifier.width(56.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumList(albums: List<AlbumCounts>) {
    if (albums.isEmpty()) {
        Text(Tr.Statistics.noItemsInList(), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Card {
        Column {
            albums.forEachIndexed { index, album ->
                ListItem(
                    headlineContent = { Text(album.name) },
                    supportingContent = album.artist?.let { artist -> { Text(artist) } },
                    trailingContent = { Text(SpotifyTr.Stats.albumCounts(liked = album.liked, trashed = album.trashed)) }
                )
                if (index < albums.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
