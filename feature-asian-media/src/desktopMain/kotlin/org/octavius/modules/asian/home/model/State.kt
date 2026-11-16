package org.octavius.modules.asian.home.model

data class DashboardItem(
    val id: Int,
    val mainTitle: String
)

data class AsianMediaHomeState(
    val data: DashboardData? = null,
    val isLoading: Boolean = true
)

data class DashboardData(
    val totalTitles: Long,
    val readingCount: Long,
    val completedCount: Long,
    val planToReadCount: Long,
    val trashCount: Long,
    val currentlyReading: List<DashboardItem>,
    val recentlyAdded: List<DashboardItem>
)