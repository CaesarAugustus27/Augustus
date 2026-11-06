package org.octavius.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.octavius.dialog.DialogWrapper
import org.octavius.dialog.GlobalDialogManager
import org.octavius.navigation.AppNavigationState
import org.octavius.navigation.AppRouter
import org.octavius.navigation.Tab
import org.octavius.ui.navigation.AppTopBar
import org.octavius.ui.navigation.MainTopAppBar
import org.octavius.ui.navigation.TabContent
import org.octavius.ui.snackbar.SnackbarManager

/**
 * Układ głównego okna: górny pasek z tytułem bieżącego ekranu, pasek zakładek i stos aktywnej zakładki.
 * Pokazuje też dialogi z `GlobalDialogManager` i snackbary z `SnackbarManager`.
 */
object MainScreen {

    /**
     * Rysuje układ według `AppRouter.state`; przed `AppRouter.initialize` nie rysuje nic. Na pasek trafiają
     * tylko zakładki z [Tab.isVisibleInNavBar].
     */
    @Composable
    fun Content(tabs: List<Tab>) {
        val navState by AppRouter.state.collectAsState()
        if (navState == null) return


        val visibleTabs = remember(tabs) { tabs.filter { it.isVisibleInNavBar } }
        val activeTabState = navState!!
        val currentScreen = activeTabState.tabStacks[activeTabState.activeTab]?.lastOrNull()
        val screenStackSize = activeTabState.tabStacks[activeTabState.activeTab]?.size ?: 0

        val snackbarHostState = remember { SnackbarHostState() }
        SnackbarManager.HandleSnackbar(snackbarHostState)

        val dialog by GlobalDialogManager.dialogConfig.collectAsState()
        DialogWrapper(config = dialog)

        Scaffold(
            topBar = {
                MainTopAppBar(
                    title = currentScreen?.title ?: "",
                    showBackButton = screenStackSize > 1,
                    onBackClicked = { AppRouter.goBack() },
                    onSettingsClicked = { AppRouter.switchToTab("settings") }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                AppTopBar(
                    tabs = visibleTabs,
                    currentState = activeTabState,
                    onTabSelected = { tab -> AppRouter.switchToTab(tab) }
                )

                TabContentAnimator(
                    navState = activeTabState,
                    visibleTabs = visibleTabs,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TabContentAnimator(
    navState: AppNavigationState,
    visibleTabs: List<Tab>,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = navState.activeTab,
        transitionSpec = {
            val initialIndex = visibleTabs.indexOf(initialState)
            val targetIndex = visibleTabs.indexOf(targetState)

            if (initialIndex == -1 || targetIndex == -1) {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            } else {
                val direction = if (targetIndex > initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
                slideIntoContainer(
                    towards = direction, animationSpec = tween(300)
                ) togetherWith slideOutOfContainer(
                    towards = direction, animationSpec = tween(300)
                )
            }
        },
        modifier = modifier
    ) { targetTab ->
        // Renderowanie zawartości aktywnego taba
        val activeStack = navState.tabStacks[targetTab] ?: emptyList()
        if (activeStack.isNotEmpty()) {
            TabContent(screenStack = activeStack)
        }
    }
}