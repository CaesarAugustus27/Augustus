package org.octavius.extension.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import io.github.octaviusframework.i18n.core.OctaviusI18n
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.octavius.extension.asian.AsianMediaExtensionModule
import org.octavius.extension.localization.PopupTr
import org.octavius.extension.util.chrome
import org.octavius.navigation.Screen
import org.octavius.shared.contract.ParseResult
import org.octavius.theme.AppTheme
import kotlin.coroutines.resume


@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Popup mówi językiem przeglądarki; język bez tłumaczeń spada na angielski
    OctaviusI18n.currentLanguage = window.navigator.language.substringBefore('-')

    // Moduły wtyczki
    val extensionModules: List<ExtensionModule> = listOf(
        AsianMediaExtensionModule
    )
    val modulesById = extensionModules.associateBy { it.id }
    // Wtyczka
    ComposeViewport {
        // Stan przechowujący aktualnie wyświetlany ekran
        var currentScreen by remember { mutableStateOf<Screen?>(null) }
        var statusMessage by remember { mutableStateOf(PopupTr.Status.analyzing()) }

        LaunchedEffect(Unit) {
            val responseString = sendParseRequestToContentScript()

            if (responseString == null) {
                statusMessage = PopupTr.Status.parseError()
                return@LaunchedEffect
            }

            val parseResult = try {
                Json.decodeFromString<ParseResult>(responseString)
            } catch (e: Exception) {
                println("Błąd deserializacji kontenera ParseResult: ${e.message}")
                null
            }

            if (parseResult == null) {
                statusMessage = PopupTr.Status.invalidData()
                return@LaunchedEffect
            }

            val targetModule = modulesById[parseResult.moduleId]

            if (targetModule == null) {
                statusMessage =PopupTr.Status.moduleNotFound(parseResult.moduleId)
                return@LaunchedEffect
            }

            // 3. Poproś ten konkretny moduł o deserializację jego danych i utworzenie ekranu
            val screenToShow = targetModule.createScreenFromJson(parseResult.dataJson)

            if (screenToShow != null) {
                currentScreen = screenToShow
            } else {
                statusMessage = PopupTr.Status.processingError()
            }
        }

        // Główny widok wtyczki
        AppTheme {
            Surface(modifier = Modifier.fillMaxSize().width(400.dp)) {
                if (currentScreen != null) {
                    // Mamy konkretny ekran, więc go renderujemy
                    currentScreen!!.Content()
                } else {
                    // Nie mamy ekranu, pokazujemy status (ładowanie, błąd, brak parsera)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(statusMessage)
                    }
                }
            }
        }
    }
}

/**
 * Wysyła generyczne żądanie parsowania do content scriptu na aktywnej karcie
 * i asynchronicznie oczekuje na odpowiedź.
 *
 * @return String JSON zawierający sparsowane dane, lub null, jeśli wystąpił błąd,
 *         nie znaleziono parsera, lub odpowiedź była nieprawidłowa.
 */
suspend fun sendParseRequestToContentScript(): String? {
    // Używamy suspendCancellableCoroutine, aby ładnie opakować API oparte na callbackach.
    return suspendCancellableCoroutine { continuation ->
        // 1. Znajdź aktywną kartę w bieżącym oknie.
        val queryInfo = js("{ active: true, currentWindow: true }")
        chrome.tabs.query(queryInfo) { tabs ->
            val activeTab = tabs.firstOrNull()
            val tabId = activeTab?.id

            // Sprawdzenie, czy udało się znaleźć aktywną kartę.
            if (tabId == null) {
                println("Błąd: Nie można znaleźć aktywnej karty.")
                continuation.resume(null) // Zakończ korutynę z wynikiem null.
                return@query
            }

            // 2. Zdefiniuj i wyślij wiadomość do content scriptu.
            val message = js("{ action: 'parsePage' }")
            chrome.tabs.sendMessage(tabId, message) { response ->
                // Sprawdzenie, czy przeglądarka nie jest w trakcie zamykania korutyny.
                if (!continuation.isActive) return@sendMessage
                println("Odp JSON: ${JSON.stringify(response)}")
                // 3. Przetwórz odpowiedź od content scriptu.
                if (js("typeof response === 'undefined' || !response.success")) {
                    // Jeśli odpowiedź jest niezdefiniowana lub flaga 'success' to false.
                    val errorMsg = response?.error as? String ?: "Brak odpowiedzi od content scriptu."
                    println("Błąd parsowania: $errorMsg")
                    continuation.resume(null)
                } else {
                    // Sukces! Otrzymaliśmy dane.
                    val jsonString = response.data as? String
                    if (jsonString != null) {
                        continuation.resume(jsonString) // Zwróć string JSON.
                    } else {
                        println("Błąd: Odpowiedź sukcesu nie zawiera danych w formacie string.")
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
