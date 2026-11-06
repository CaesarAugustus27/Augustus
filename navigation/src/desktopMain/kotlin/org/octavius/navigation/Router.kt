package org.octavius.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Aktywna zakładka i stos ekranów każdej zakładki, od ekranu głównego do tego na szczycie.
 */
data class AppNavigationState(
    val activeTab: Tab,
    val tabStacks: Map<Tab, List<Screen>>
)

/**
 * Nawigacja całej aplikacji: każda zakładka ma własny stos ekranów, a bieżący stan wystawia [state].
 * Reszty metod można używać dopiero po [initialize].
 */
object AppRouter {

    private lateinit var initialTabs: List<Tab>
    private lateinit var tabsById: Map<String, Tab>
    /**
     * Zakłada każdej zakładce stos z jej ekranem głównym i robi aktywną pierwszą z listy.
     *
     * @throws IllegalArgumentException gdy lista zakładek jest pusta
     */
    fun initialize(tabs: List<Tab>) {
        require(tabs.isNotEmpty()) { "Lista zakładek nie może być pusta" }

        initialTabs = tabs
        tabsById = tabs.associateBy { it.id }

        val initialStacks = tabs.associateWith { listOf(it.getInitialScreen()) }

        _state.value = AppNavigationState(
            activeTab = tabs.first(),
            tabStacks = initialStacks
        )
    }

    private val _state = MutableStateFlow<AppNavigationState?>(null)
    
    /** Bieżący stan nawigacji, `null` do wywołania [initialize]. */
    val state = _state.asStateFlow()

    /** Robi [tab] aktywną zakładką. Jej stos zostaje taki, jaki był. */
    fun switchToTab(tab: Tab) {
        _state.update { currentState ->
            currentState?.copy(activeTab = tab)
        }
    }

    /** Robi aktywną zakładkę o [Tab.id] równym [tabId]. Nieznane id tylko wypisuje ostrzeżenie na konsolę. */
    fun switchToTab(tabId: String) {
        tabsById[tabId]?.let { tab ->
            switchToTab(tab)
        } ?: println("Warning: Nie znaleziono zakładki o ID: $tabId")
    }

    /** Kładzie [screen] na szczyt stosu aktywnej zakładki. */
    fun navigateTo(screen: Screen) {
        _state.update { currentState ->
            val activeTab = currentState!!.activeTab
            val currentStack = currentState.tabStacks[activeTab]!!

            val newStack = currentStack + screen
            currentState.copy(
                tabStacks = currentState.tabStacks + (activeTab to newStack)
            )
        }
    }

    /**
     * Przełącza na zakładkę o [Tab.id] równym [tabId] i kładzie [screen] na szczyt jej stosu, w jednej zmianie
     * [state]. Nieznane id tylko wypisuje ostrzeżenie na konsolę.
     */
    fun navigateTo(screen: Screen, tabId: String) {
        val targetTab = tabsById[tabId]
        if (targetTab == null) {
            println("Warning: Próba nawigacji do nieistniejącej zakładki o ID: $tabId")
            return
        }
        _state.update { currentState ->
            val stateWithSwitchedTab = currentState!!.copy(activeTab = targetTab)
            val targetStack = stateWithSwitchedTab.tabStacks[targetTab]!!

            val newStack = targetStack + screen
            stateWithSwitchedTab.copy(
                tabStacks = stateWithSwitchedTab.tabStacks + (targetTab to newStack)
            )
        }
    }

    /** Zdejmuje ekran ze szczytu stosu aktywnej zakładki. Ekranu głównego zakładki nie zdejmuje. */
    fun goBack() {
        _state.update { currentState ->
            val activeTab = currentState!!.activeTab
            val currentStack = currentState.tabStacks[activeTab]!!

            if (currentStack.size <= 1) return@update currentState

            val newStack = currentStack.dropLast(1)
            currentState.copy(
                tabStacks = currentState.tabStacks + (activeTab to newStack)
            )
        }
    }
}