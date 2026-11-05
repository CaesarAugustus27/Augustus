package org.octavius.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import io.github.octaviusframework.client.OctaviusClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.Koin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.octavius.contract.ApiModule
import org.octavius.api.server.EmbeddedServer
import org.octavius.app.localization.AppTr
import org.octavius.app.settings.form.database.DatabaseSettingsFormScreen
import org.octavius.app.settings.AppSettingsManager
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.error.showUnhandledError
import org.octavius.navigation.AppRouter
import org.octavius.navigation.NavigationEvent
import org.octavius.navigation.NavigationEventBus
import org.octavius.navigation.Tab
import java.awt.Frame

// Prosty enum do zarządzania stanem aplikacji
private enum class AppState {
    Loading,       // Aplikacja ładuje zasoby
    DatabaseError, // Błąd połączenia z bazą danych
    Ready          // Aplikacja jest gotowa do pracy
}

fun main() {
    // Wyjątek, który przeleciał przez wszystko (np. korutyna na Dispatchers.IO bez własnego handlera),
    // trafia do logu i dialogu, a nie tylko na konsolę.
    Thread.setDefaultUncaughtExceptionHandler { _, error -> showUnhandledError(error) }

    val settingsManager = AppSettingsManager()
    settingsManager.applySettings()

    // Przed bazą, bo schematy featurów wyznaczają search_path i to, co się migruje.
    val features = discoverFeatures()
    val featureSchemas = features.mapNotNull { it.schema }

    val koin = startKoin {
        printLogger()
        allowOverride(true)
        modules(
            module { single { settingsManager } },
            databaseModule(featureSchemas)
        )
    }.koin

    application(exitProcessOnExit = false) {
        var appState by remember { mutableStateOf(AppState.Loading) }

        when (appState) {
            AppState.Loading -> {
                AppLoadingScreen(
                    onLoaded = { appState = AppState.Ready },
                    onError = { appState = AppState.DatabaseError },
                    koin = koin,
                    settingsManager = settingsManager
                )
            }

            AppState.DatabaseError -> {
                DatabaseErrorWindow(
                    onCloseRequest = ::exitApplication,
                    onRetry = {
                        // Re-load the database module to pick up new settings
                        koin.loadModules(listOf(databaseModule(featureSchemas)))
                        appState = AppState.Loading
                    },
                    settingsManager = settingsManager
                )
            }

            AppState.Ready -> {
                MainAppScreen(features, onCloseRequest = ::exitApplication, settingsManager = settingsManager)
            }
        }
    }
    // Closing database connections
    stopKoin()
}

@Composable
private fun ApplicationScope.AppLoadingScreen(
    onLoaded: () -> Unit,
    onError: () -> Unit,
    koin: Koin,
    settingsManager: AppSettingsManager
) {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppTr.App.loading(),
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(300.dp, 200.dp)),
        undecorated = true,
        resizable = false
    ) {
        AppThemeFromSettings(settingsManager) {
            // Okno jest undecorated i bez Surface miałoby domyślne tło AWT (białe),
            // niezależnie od wybranego motywu.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(AppTr.App.loading())
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val success = withContext(Dispatchers.IO) {
            try {
                koin.get<OctaviusClient>()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        if (success) onLoaded() else onError()
    }
}

@Composable
private fun ApplicationScope.DatabaseErrorWindow(
    onCloseRequest: () -> Unit,
    onRetry: () -> Unit,
    settingsManager: AppSettingsManager
) {
    val formScreen = DatabaseSettingsFormScreen.create(settingsManager)
    Window(
        onCloseRequest = onCloseRequest,
        title = AppTr.Settings.Database.title(),
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(600.dp, 500.dp))
    ) {
        AppThemeFromSettings(settingsManager) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        text = AppTr.Settings.Database.restartWarning(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        formScreen.Content()
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(AppTr.Settings.Database.retry())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ApplicationScope.MainAppScreen(
    features: List<FeatureModule>,
    onCloseRequest: () -> Unit,
    settingsManager: AppSettingsManager
) {
    // ==============================================================================
    //  GŁÓWNA APLIKACJA (komponowana tylko raz, gdy stan jest Ready)
    // ==============================================================================
    val (tabs, apiModules, screenFactories) = remember {
        val tabs = features.mapNotNull { it.getTab() }
        val apiModules = features.flatMap { it.getApiModules().orEmpty() }
        val screenFactories = features.flatMap { it.getScreenFactories().orEmpty() }.associateBy { it.screenId }
        Triple(tabs, apiModules, screenFactories)
    }

    AppLifecycleManager(apiModules, tabs)

    val windowRecovery = remember { MainWindowRecovery() }
    // Poza `key`, żeby odbudowane okno zachowało rozmiar i położenie.
    val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))

    key(windowRecovery.generation) {
        val exceptionHandlerFactory = remember { windowRecovery.exceptionHandlerFactory() }
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides exceptionHandlerFactory) {
            Window(
                onCloseRequest = ::exitApplication,
                title = AppTr.App.name(),
                state = windowState
            ) {
                NavigationHandler(screenFactories)

                // Renderowanie UI aplikacji
                App(tabs, settingsManager)
            }
        }
    }
}

@Composable
private fun AppLifecycleManager(apiModules: List<ApiModule>, tabs: List<Tab>) {
    val server = remember { EmbeddedServer(apiModules) }

    DisposableEffect(Unit) {
        AppRouter.initialize(tabs)

        println("Starting Ktor server in background...")

        // Uruchamiamy serwer w korutynie w tle
        val serverScope = CoroutineScope(Dispatchers.IO)
        val job = serverScope.launch {
            server.start()
        }

        onDispose {
            println("onDispose triggered. Shutting down server.")
            server.stop() // Grzecznie zatrzymujemy serwer
            job.cancel() // Anulujemy korutynę na wszelki wypadek
            serverScope.cancel() // Czyścimy cały scope
        }
    }
}

@Composable
private fun FrameWindowScope.NavigationHandler(screenFactories: Map<String, ScreenFactory>) {
    // Uzyskujemy dostęp do bazowego okna AWT
    val awtWindow = this.window

    LaunchedEffect(Unit) {
        NavigationEventBus.events.collectLatest { event ->
            when (event) {
                is NavigationEvent.Navigate -> {
                    if (event.screenId == null) {
                        AppRouter.switchToTab(event.tabId)
                    } else {
                        screenFactories[event.screenId]?.let { factory ->
                            val screen = factory.create(event.payload)
                            AppRouter.navigateTo(screen, event.tabId)
                        } ?: println("Error: No factory for screenId: ${event.screenId}")
                    }
                }
            }
            bringWindowToFront(awtWindow)
        }
    }
}

private fun bringWindowToFront(window: Frame) {
    if (window.extendedState == Frame.ICONIFIED) {
        window.extendedState = Frame.NORMAL
    }
    window.isAlwaysOnTop = true
    window.isAlwaysOnTop = false
    window.requestFocus()
}
