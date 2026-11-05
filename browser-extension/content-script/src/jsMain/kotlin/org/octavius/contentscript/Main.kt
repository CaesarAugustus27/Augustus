package org.octavius.contentscript

import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.octavius.shared.contract.ParseResult
import org.octavius.shared.contract.ParsedData
import org.octavius.shared.contract.Parser
import org.octavius.extension.util.chrome
import org.octavius.modules.asian.parser.MangaUpdatesParser
import org.octavius.modules.asian.parser.NovelUpdatesParser

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    println("Augustus Content Script (z logiką) załadowany!")

    if (window.location.hostname == "steamdb.info") listenToSteamDbIgnores()

    val availableParsers: List<Parser<*>> = listOf(
        MangaUpdatesParser,
        NovelUpdatesParser
    )

    chrome.runtime.onMessage.addListener { message, _, sendResponse ->
        when (message.action) {
            "parsePage" -> GlobalScope.launch { // Parsowanie może być asynchroniczne
                val url = window.location.href
                val parser = availableParsers.firstOrNull { it.canParse(url) }

                if (parser != null) {
                    val parsedData = parser.parse()
                    if (parsedData != null) {
                        @Suppress("UNCHECKED_CAST")
                        val dataJsonString = (parser as Parser<ParsedData>).serialize(parsedData)

                        val resultContainer = ParseResult(
                            moduleId = parser.moduleId,
                            dataJson = dataJsonString
                        )
                        val finalJsonString = Json.encodeToString(resultContainer)
                        // Tworzymy obiekt odpowiedzi JS, który zostanie wysłany.
                        val responsePayload = js("({})")
                        responsePayload.success = true
                        responsePayload.data = finalJsonString // JSON jako string w polu 'data'

                        sendResponse(responsePayload)
                    } else {
                        sendResponse(js("{ success: false, error: 'Parsing failed' }"))
                    }
                } else {
                    sendResponse(js("{ success: false, error: 'No suitable parser found' }"))
                }
            }

            // Tło wtyczki podsłuchało przeniesienie serii na liście lektur i pyta, co to za seria.
            // null, gdy serii nie ma na tej stronie - tło da wtedy znać, że nic nie wysłało.
            "describeNovelUpdatesListMove" -> {
                val move = NovelUpdatesParser.listMove(message.sid as String, message.listId as Int)
                sendResponse(move?.let { Json.encodeToString(it) })
            }

            "showAugustusToast" -> showToast(message.text as String, message.saved as Boolean)
        }
        true
    }
}
