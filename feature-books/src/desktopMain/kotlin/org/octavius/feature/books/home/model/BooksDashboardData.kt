package org.octavius.feature.books.home.model

data class BookDashboardItem(
    val id: Int,
    val title: String
)

data class BooksDashboardData(
    val totalBooks: Long,
    val totalAuthors: Long,
    val readingCount: Long,
    val completedCount: Long,
    val currentlyReading: List<BookDashboardItem>,
    val recentlyAdded: List<BookDashboardItem>
)
