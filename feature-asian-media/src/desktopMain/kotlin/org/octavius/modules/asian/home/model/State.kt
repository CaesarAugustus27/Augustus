package org.octavius.modules.asian.home.model

data class DashboardItem(
    val id: Int,
    val mainTitle: String
)

data class AsianMediaHomeState(
    val totalTitles: Long = 0,
    val readingCount: Long = 0,
    val completedCount: Long = 0,
    val planToReadCount: Long = 0,
    val trashCount: Long = 0,
    val currentlyReading: List<DashboardItem> = emptyList(),
    val recentlyAdded: List<DashboardItem> = emptyList(),
    val isLoading: Boolean = true
)

data class DashboardData(
    val totalTitles: Long,
    val readingCount: Long,
    val completedCount: Long,
    val planToReadCount: Long,
    val trashCount: Long,
    val currentlyReading: List<DashboardItem>?, // Lista może być NULL, jeśli zapytanie nic nie zwróci
    val recentlyAdded: List<DashboardItem>?
)