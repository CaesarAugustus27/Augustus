package org.octavius.feature.books.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.octavius.error.rememberSupervisedCoroutineScope
import org.octavius.feature.books.form.book.ui.BookFormScreen
import org.octavius.feature.books.home.model.BookDashboardItem
import org.octavius.feature.books.home.model.BooksDashboardData
import org.octavius.feature.books.home.model.BooksHomeHandler
import org.octavius.feature.books.localization.BooksTr
import org.octavius.feature.books.report.ui.AuthorsReportScreen
import org.octavius.feature.books.report.ui.BooksReportScreen
import org.octavius.navigation.AppRouter
import org.octavius.navigation.Screen

class BooksHomeScreen(override val title: String) : Screen {

    @Composable
    override fun Content() {
        val composableScope = rememberSupervisedCoroutineScope()
        val handler = remember(composableScope) { BooksHomeHandler(composableScope) }
        val dashboard by handler.data.collectAsState()

        LaunchedEffect(Unit) {
            handler.loadData()
        }

        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(BooksTr.Home.addBook()) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        AppRouter.navigateTo(BookFormScreen.create())
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.End
        ) { paddingValues ->
            when (val data = dashboard) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Sekcja statystyk
                        item { StatsHeader(data) }
                        // Przyciski nawigacyjne do pełnych list (Książki / Autorzy)
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Przycisk: Lista Książek
                                OutlinedButton(
                                    onClick = {
                                        AppRouter.navigateTo(BooksReportScreen.create())
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Article, null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(BooksTr.Home.allBooks())
                                }

                                // Przycisk: Lista Autorów
                                OutlinedButton(
                                    onClick = {
                                        AppRouter.navigateTo(AuthorsReportScreen.create())
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Person, null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(BooksTr.Home.allAuthors())
                                }
                            }
                        }

                        // Szybki dostęp: Czytane teraz
                        item {
                            QuickAccessList(
                                title = BooksTr.Home.currentlyReading(),
                                items = data.currentlyReading,
                                onItemClick = { item ->
                                    AppRouter.navigateTo(BookFormScreen.create(entityId = item.id))
                                }
                            )
                        }

                        // Szybki dostęp: Ostatnio dodane
                        item {
                            QuickAccessList(
                                title = BooksTr.Home.recentlyAdded(),
                                items = data.recentlyAdded,
                                onItemClick = { item ->
                                    AppRouter.navigateTo(BookFormScreen.create(entityId = item.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun create(): Screen {
            return BooksHomeScreen(BooksTr.Home.title())
        }
    }
}

@Composable
private fun StatsHeader(data: BooksDashboardData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(BooksTr.Stats.total(), data.totalBooks.toString(), Modifier.weight(1f))
        StatCard(BooksTr.Stats.authors(), data.totalAuthors.toString(), Modifier.weight(1f))
        StatCard(BooksTr.Stats.reading(), data.readingCount.toString(), Modifier.weight(1f))
        StatCard(BooksTr.Stats.completed(), data.completedCount.toString(), Modifier.weight(1f))
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

@Composable
private fun QuickAccessList(
    title: String,
    items: List<BookDashboardItem>,
    onItemClick: (BookDashboardItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (items.isEmpty()) {
            Text(
                text = BooksTr.Home.noItems(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        } else {
            Card {
                Column {
                    items.forEachIndexed { index, item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable { onItemClick(item) }
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