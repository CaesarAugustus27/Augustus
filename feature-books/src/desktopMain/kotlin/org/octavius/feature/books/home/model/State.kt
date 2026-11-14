package org.octavius.feature.books.home.model

sealed interface BooksHomeState {
    data object Loading : BooksHomeState
    data class Success(val data: BooksDashboardData) : BooksHomeState
    data object Error : BooksHomeState
}

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
