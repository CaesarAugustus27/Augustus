package org.octavius.feature.books.home.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BooksHomeHandler(
    private val scope: CoroutineScope
) : KoinComponent {
    private val db: OctaviusClient by inject()

    private val _data = MutableStateFlow<BooksDashboardData?>(null)

    /** `null`, dopóki dane się nie wczytają. */
    val data = _data.asStateFlow()

    private fun getSql(): String {
        // Liczba wszystkich książek
        val totalBooksSubquery = db.select("COUNT(*)").from("books.books").toSql()

        // Liczba wszystkich autorów
        val totalAuthorsSubquery = db.select("COUNT(*)").from("books.authors").toSql()

        // Liczba książek "W trakcie czytania"
        val readingCountSubquery = db.select("COUNT(*)")
            .from("books.books")
            .where("status = 'READING'")
            .toSql()

        // Liczba przeczytanych
        val completedCountSubquery = db.select("COUNT(*)")
            .from("books.books")
            .where("status = 'COMPLETED'")
            .toSql()

        // Lista: Aktualnie czytane (ostatnio aktualizowane)
        val innerCurrentlyReading = db.select("id, title_pl")
            .from("books.books")
            .where("status = 'READING'")
            .orderBy("updated_at DESC")
            .limit(5)
            .toSql()

        val currentlyReadingSubquery = db.select(
            """
            COALESCE(array_agg(ROW('id', id, 'title', title_pl)), '{}')
        """
        ).fromSubquery(innerCurrentlyReading).toSql()

        // Lista: Ostatnio dodane
        val innerRecentlyAdded = db.select("id, title_pl")
            .from("books.books")
            .orderBy("created_at DESC")
            .limit(5)
            .toSql()

        val recentlyAddedSubquery = db.select(
            """
            COALESCE(array_agg(ROW('id', id, 'title', title_pl)), '{}')
        """
        ).fromSubquery(innerRecentlyAdded).toSql()

        // Złożenie wszystkiego w jeden SELECT
        return """
            ($totalBooksSubquery) AS total_books,
            ($totalAuthorsSubquery) AS total_authors,
            ($readingCountSubquery) AS reading_count,
            ($completedCountSubquery) AS completed_count,
            ($currentlyReadingSubquery) AS currently_reading,
            ($recentlyAddedSubquery) AS recently_added
        """
    }

    /** Wyjątek idzie do `showUnhandledError` scope'u, a [data] zostaje `null`. */
    fun loadData() {
        scope.launch {
            val finalSelectClause = getSql()
            _data.value = withContext(OctaviusDispatchers.Virtual) {
                db.select(finalSelectClause).fetchObjectStrict<BooksDashboardData>()
            }
        }
    }
}