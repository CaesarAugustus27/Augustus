package org.octavius.report.component

import androidx.compose.runtime.compositionLocalOf
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.report.ReportDataResult
import org.octavius.report.ReportEvent
import org.octavius.report.configuration.ReportConfiguration
import org.octavius.report.configuration.ReportConfigurationManager
import org.octavius.report.configuration.SortDirection

val LocalReportHandler = compositionLocalOf<ReportHandler> { error("No ReportHandler provided") }

/**
 * Główny kontroler systemu raportowania - zarządza stanem i logiką biznesową raportów.
 *
 * ReportHandler jest centralnym komponentem systemu raportowania, który:
 * - Zarządza reaktywnym stanem raportu (dane, filtrowanie, sortowanie, paginacja)
 * - Obsługuje zdarzenia użytkownika i aktualizuje stan
 * - Koordynuje pobieranie danych z bazy przez DataManager
 * - Zarządza konfiguracją raportów (zapisywanie/ładowanie ustawień)
 *
 * Architektura oparta na wzorcu Redux:
 * 1. UI wysyła zdarzenia (ReportEvent) do handlera
 * 2. Handler redukuje stan na podstawie zdarzenia
 * 3. Jeśli potrzeba, wysyła żądanie pobrania danych
 * 4. UI automatycznie się przerysowuje na podstawie nowego stanu
 *
 * @param reportStructureBuilder Builder definicji struktury raportu (kolumny, zapytania, akcje).
 * @param handlerScope opcjonalny CoroutineScope
 */
class ReportHandler(
    reportStructureBuilder: ReportStructureBuilder,
    private val handlerScope: CoroutineScope = CoroutineScope(SupervisorJob() + OctaviusDispatchers.Virtual)
) {
    val reportStructure: ReportStructure = reportStructureBuilder.build()


    fun cancelJobs() {
        handlerScope.coroutineContext[Job]?.cancelChildren()
    }

    private var dataFetchJob: Job? = null

    // Inicjalizujemy stan początkowy.
    private val _state: MutableStateFlow<ReportState> = MutableStateFlow(ReportState.initial(reportStructure))
    val state = _state.asStateFlow()

    private val dataManager = ReportDataManager(reportStructure)
    private val configManager = ReportConfigurationManager()

    init {
        // Załadowanie domyślnej konfiguracji
        loadDefaultConfiguration()
    }

    //------------------------------------------Event Handling----------------------------------------------------------

    /**
     * Główna metoda obsługi zdarzeń użytkownika.
     *
     * Implementuje wzorzec Redux/MVI:
     * 1. Redukuje aktualny stan z nowym zdarzeniem
     * 2. Aktualizuje stan tylko jeśli nastąpiła zmiana
     * 3. Sprawdza czy zdarzenie wymaga przeładowania danych z bazy
     * 4. Jeśli tak, uruchamia asynchroniczne pobieranie
     *
     * @param event Zdarzenie użytkownika do przetworzenia.
     */
    fun onEvent(event: ReportEvent) {
        val currentState = _state.value
        val newState = reduceState(currentState, event)
        
        // Aktualizuj stan
        if (newState != currentState) {
            _state.value = newState
        }
        
        // Sprawdź czy event wymaga przeładowania danych
        if (event.triggersDataReload()) {
            fetchData(newState)
        }
    }

    private fun reduceState(currentState: ReportState, event: ReportEvent): ReportState {
        return when (event) {
            is ReportEvent.SearchQueryChanged -> currentState.copy(
                searchQuery = event.query,
                pagination = currentState.pagination.resetPage()
            )
            is ReportEvent.FilterChanged -> {
                val newFilters = currentState.filterData + (event.columnKey to event.newFilterData)
                currentState.copy(filterData = newFilters, pagination = currentState.pagination.resetPage())
            }
            is ReportEvent.ClearFilter -> {
                val defaultFilterData = reportStructure.getColumn(event.columnKey).createFilterAndFilterData()!!
                val newFilters = currentState.filterData + (event.columnKey to defaultFilterData)
                currentState.copy(filterData = newFilters, pagination = currentState.pagination.resetPage())
            }
            is ReportEvent.PageChanged -> currentState.copy(
                pagination = currentState.pagination.copy(currentPage = event.newPage)
            )
            is ReportEvent.PageSizeChanged -> currentState.copy(
                pagination = currentState.pagination.copy(pageSize = event.newSize).resetPage()
            )
            is ReportEvent.SortOrderChanged -> currentState.copy(
                sortOrder = event.newSortOrder,
                pagination = currentState.pagination.resetPage()
            )
            is ReportEvent.ColumnOrderChanged -> currentState.copy(columnKeysOrder = event.newColumnOrder)
            is ReportEvent.ColumnVisibilityChanged -> currentState.copy(visibleColumns = event.newVisibleColumns)
            is ReportEvent.ApplyConfiguration -> applyConfiguration(currentState, event.configuration)
            // Eventy które nie zmieniają stanu bezpośrednio
            is ReportEvent.Initialize -> currentState
        }
    }

    private fun fetchData(state: ReportState) {
        dataFetchJob?.cancel()
        dataFetchJob = handlerScope.launch {

            _state.value = _state.value.copy(isLoading = true, error = null)

            // Błąd bazy wraca jako ReportDataResult.Failure, nie wyjątkiem.
            val result = dataManager.fetchData(state)

            // Zapytanie blokuje, więc anulowanie go nie przerywa. Job anulowany w trakcie, bo przyszło nowsze
            // zapytanie, kończy się tutaj i nie nadpisuje jego wyniku.
            ensureActive()

            when (result) {
                is ReportDataResult.Success -> {
                    // Sukces! Mamy dane i nową paginację.
                    _state.value = _state.value.copy(
                        isLoading = false,
                        data = result.data,
                        pagination = result.paginationState
                    )
                }
                is ReportDataResult.Failure -> {
                    GlobalDialogManager.show(ErrorDialogConfig(result.error))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Błąd pobierania danych z bazy."
                    )
                }
            }
        }
    }

    //-------------------------------------------Configuration----------------------------------------------------------

    private fun applyConfiguration(state: ReportState, configuration: ReportConfiguration): ReportState {
        // Układ mógł powstać przy innym zestawie kolumn. Filtry, sortowanie i widoczność kolumn, których już nie
        // ma, odpadają.
        val allColumnKeys = reportStructure.getAllColumns().keys

        // Deserializuj filtry, tworząc nową mapę stanów filtrów
        val newFilterDataMap = state.filterData.toMutableMap()
        configuration.filters.filter { it.columnName in allColumnKeys }.forEach { filterConfig ->
            val columnKey = filterConfig.columnName
            val column = reportStructure.getColumn(columnKey)

            if (column.filter != null) {
                // Używamy fabryki z klasy Filter do stworzenia nowej instancji FilterData
                val newFilterState = column.filter!!.deserializeData(filterConfig.config)
                newFilterDataMap[columnKey] = newFilterState
            }
        }

        // Kolumny dodane później trafiają na koniec jako ukryte, bo zarządzanie kolumnami pokazuje tylko
        // columnKeysOrder i inaczej nie dałoby się ich włączyć.
        val columnOrder = configuration.columnOrder.filter { it in allColumnKeys } +
                (allColumnKeys - configuration.columnOrder.toSet())

        return state.copy(
            pagination = state.pagination.copy(currentPage = 0, pageSize = configuration.pageSize),
            visibleColumns = configuration.visibleColumns.filter { it in allColumnKeys }.toSet(),
            columnKeysOrder = columnOrder,
            sortOrder = configuration.sortOrder.filter { it.columnName in allColumnKeys }
                .map { (columnName, sortDirection) -> columnName to sortDirection },
            filterData = newFilterDataMap
        )
    }

    private fun loadDefaultConfiguration() {
        val defaultConfig = configManager.loadDefaultConfiguration(reportStructure.reportName)
        if (defaultConfig != null) {
            _state.value = applyConfiguration(_state.value, defaultConfig)
        }
    }

    //-------------------------------------Sorting and visibility handling----------------------------------------------

    fun toggleColumnVisibility(
        columnKey: String,
        isVisible: Boolean
    ) {
        if (isVisible) {
            val visibleColumns = state.value.visibleColumns.toMutableSet()
            visibleColumns.remove(columnKey)
            onEvent(ReportEvent.ColumnVisibilityChanged(visibleColumns.toSet()))
        } else {
            val visibleColumns = state.value.visibleColumns.toMutableSet()
            visibleColumns.add(columnKey)
            onEvent(ReportEvent.ColumnVisibilityChanged(visibleColumns.toSet()))
        }
    }

    fun reorderColumns(fromIndex: Int, toIndex: Int) {
        val columnKeysOrder = state.value.columnKeysOrder.toMutableList()
        val item = columnKeysOrder.removeAt(fromIndex)
        columnKeysOrder.add(toIndex, item)
        onEvent(ReportEvent.ColumnOrderChanged(columnKeysOrder))
    }

    fun updateSortDirection(
        columnKey: String,
        newDirection: SortDirection
    ) {
        val currentSort = state.value.sortOrder.toMutableList()
        val index = currentSort.indexOfFirst { it.first == columnKey }
        if (index >= 0) {
            currentSort[index] = columnKey to newDirection
            onEvent(ReportEvent.SortOrderChanged(currentSort))
        }
    }

    fun removeSortColumn(
        columnKey: String
    ) {
        val newSort = state.value.sortOrder.filter { it.first != columnKey }
        onEvent(ReportEvent.SortOrderChanged(newSort))
    }

    fun addSortColumn(
        columnKey: String
    ): Boolean {
        val currentSort = state.value.sortOrder
        if (!currentSort.any { it.first == columnKey }) {
            val newSort = state.value.sortOrder.toMutableList()
            newSort.add(columnKey to SortDirection.Ascending)
            onEvent(ReportEvent.SortOrderChanged(newSort))
            return true
        }
        return false
    }

    fun reorderSortColumns(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val newSort = state.value.sortOrder.toMutableList()
        val item = newSort.removeAt(fromIndex)
        newSort.add(toIndex, item)
        onEvent(ReportEvent.SortOrderChanged(newSort))
    }

}
