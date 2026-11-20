package org.octavius.modules.asian.home.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AsianMediaHomeHandler(
    private val scope: CoroutineScope
) : KoinComponent {
    private val db: OctaviusClient by inject()

    private val _data = MutableStateFlow<DashboardData?>(null)

    /** `null`, dopóki dane się nie wczytają. */
    val data = _data.asStateFlow()

    init {
        loadData()
    }

    fun getSql(): String {
        val totalTitlesSubquery = db.select("COUNT(*)").from("asian_media.titles").toSql()

        val readingCountSubquery = db.select("COUNT(DISTINCT title_id)")
            .from("asian_media.publications")
            .where("status = 'READING'")
            .toSql()

        val readingPublication = db.select("1")
            .from("asian_media.publications p2")
            .where("p2.title_id = p1.title_id AND p2.status = 'READING'")
            .toSql()
        val completedCountSubquery =
            db.select("COUNT(DISTINCT p1.title_id)")
                .from("asian_media.publications p1")
                .where("p1.status = 'COMPLETED' AND NOT EXISTS ($readingPublication)")
                .toSql()
        val planToReadCountSubquery =
            db.select("COUNT(DISTINCT p1.title_id)")
                .from("asian_media.publications p1")
                .where("p1.status = 'PLAN_TO_READ' AND NOT EXISTS ($readingPublication)")
                .toSql()

        // Śmieć to tytuł, którego wszystkie publikacje są w TRASH.
        val notTrashPublication = db.select("1")
            .from("asian_media.publications p2")
            .where("p2.title_id = p1.title_id AND p2.status <> 'TRASH'")
            .toSql()
        val trashCountSubquery =
            db.select("COUNT(DISTINCT p1.title_id)")
                .from("asian_media.publications p1")
                .where("p1.status = 'TRASH' AND NOT EXISTS ($notTrashPublication)")
                .toSql()

        val innerCurrentlyReading = db.select("t.id, t.titles")
            .from("asian_media.titles t")
            .where("EXISTS (SELECT 1 FROM asian_media.publications p WHERE p.title_id = t.id AND p.status = 'READING')")
            .orderBy("t.updated_at DESC")
            .limit(5)
            .toSql()
        // Bez wierszy array_agg daje NULL, a listy w DashboardData są niepuste
        val currentlyReadingSubquery = db.select(
            """
            COALESCE(array_agg(ROW('id', id, 'main_title', titles[1])), '{}')"""
        ).fromSubquery(innerCurrentlyReading)
            .toSql()

        val innerRecentlyAdded = db.select("t.id, t.titles")
            .from("asian_media.titles t")
            .where("EXISTS (SELECT 1 FROM asian_media.publications p WHERE p.title_id = t.id AND p.status <> 'TRASH')")
            .orderBy("t.created_at DESC")
            .limit(5)
            .toSql()
        val recentlyAddedSubquery = db.select(
            """
            COALESCE(array_agg(ROW('id', id, 'main_title', titles[1])), '{}')"""
        ).fromSubquery(innerRecentlyAdded)
            .toSql()

        // === Składamy finalną klauzulę SELECT z gotowych klocków ===

        return """
        ($totalTitlesSubquery) AS total_titles,
        ($readingCountSubquery) AS reading_count,
        ($completedCountSubquery) AS completed_count,
        ($planToReadCountSubquery) AS plan_to_read_count,
        ($trashCountSubquery) AS trash_count,
        ($currentlyReadingSubquery) AS currently_reading,
        ($recentlyAddedSubquery) AS recently_added
    """
    }

    /** Wyjątek idzie do `showUnhandledError` scope'u, a [data] zostaje `null`. */
    fun loadData() {
        scope.launch {
            val finalSelectClause = getSql()
            _data.value = withContext(OctaviusDispatchers.Virtual) {
                db.select(finalSelectClause).fetchObjectStrict<DashboardData>()
            }
        }
    }
}