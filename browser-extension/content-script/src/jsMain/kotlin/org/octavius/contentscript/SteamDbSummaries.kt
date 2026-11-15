package org.octavius.contentscript

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.octavius.extension.util.chrome
import org.octavius.modules.games.parser.SteamDbGameRow
import org.octavius.modules.games.parser.SteamDbParser
import org.octavius.modules.games.parser.SteamStore
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val SUMMARY_CLASS = "augustus-steam-summary"

/** Opis i tagi jednej gry, gotowe do wstawienia. */
private class Summary(val description: String?, val tags: List<String>)

// Gry opisane na tej stronie; null to gra, której sklep nie zna albo nie ma czego pokazać.
private val summaries = mutableMapOf<Int, Summary?>()
private val requested = mutableSetOf<Int>()
private var scanScheduled = false
private var failureShown = false

@OptIn(DelicateCoroutinesApi::class)
private val tagNames by lazy {
    GlobalScope.async { fetchSteamApi(SteamStore.tagListUrl())?.let(SteamStore::parseTagNames).orEmpty() }
}

/**
 * Pod nazwami gier w tabelach SteamDB (wyprzedaże, wykresy, wyszukiwarka) wstawia krótki opis i tagi ze
 * sklepu Steama, których SteamDB na listach nie pokazuje.
 *
 * Tabele same dociągają i przerysowują wiersze (stronicowanie, filtry), więc wiersze bez opisu zbiera
 * obserwator DOM i pyta o nie paczkami. Opis stoi obok linku z nazwą, a nie w nim, więc podsłuch
 * „Ignore” czyta nazwę jak dotąd.
 */
fun showSteamStoreSummaries() {
    val body = document.body ?: return
    MutationObserver { _, _ -> scheduleScan() }.observe(body, MutationObserverInit(childList = true, subtree = true))
    scheduleScan()
}

// Tabela dokłada wiersze seriami, a jedno zapytanie na serię wystarczy.
private fun scheduleScan() {
    if (scanScheduled) return
    scanScheduled = true
    window.setTimeout({
        scanScheduled = false
        scan()
    }, 200)
}

@OptIn(DelicateCoroutinesApi::class)
private fun scan() {
    val rows = SteamDbParser.gameRows().filter { it.nameLink.parentElement?.querySelector(".$SUMMARY_CLASS") == null }
    rows.forEach { row -> summaries[row.appId]?.let { insert(row, it) } }

    val missing = rows.map { it.appId }.distinct().filter { it !in summaries && it !in requested }
    requested += missing
    missing.chunked(SteamStore.MAX_ITEMS_PER_REQUEST).forEach { batch -> GlobalScope.launch { load(batch) } }
}

private suspend fun load(appIds: List<Int>) {
    val body = fetchSteamApi(SteamStore.itemsUrl(appIds))
    if (body == null) {
        // Następna zmiana na stronie spróbuje jeszcze raz
        requested -= appIds.toSet()
        if (!failureShown) showToast("nie udało się pobrać opisów gier ze Steama", false)
        failureShown = true
        return
    }
    val names = tagNames.await()
    val items = SteamStore.parseItems(body).associateBy { it.appId }
    appIds.forEach { appId ->
        val item = items[appId]
        val tags = item?.tagIds.orEmpty().mapNotNull { names[it] }
        summaries[appId] = if (item?.description == null && tags.isEmpty()) null else Summary(item?.description, tags)
    }
    requested -= appIds.toSet()
    scheduleScan()
}

private fun insert(row: SteamDbGameRow, summary: Summary) {
    val cell = row.nameLink.parentElement ?: return
    val box = document.createElement("div") as HTMLElement
    box.className = SUMMARY_CLASS
    box.style.cssText = "margin-top:4px;max-width:640px;font-size:12px;line-height:1.4;white-space:normal;opacity:.8"
    summary.description?.let { description ->
        box.appendChild((document.createElement("div") as HTMLElement).also { it.textContent = description })
    }
    if (summary.tags.isNotEmpty()) {
        box.appendChild((document.createElement("div") as HTMLElement).also {
            it.textContent = summary.tags.joinToString(" · ")
            it.style.cssText = "margin-top:2px;opacity:.75"
        })
    }
    cell.appendChild(box)
}

private suspend fun fetchSteamApi(url: String): String? = suspendCoroutine { continuation ->
    val message: dynamic = js("({})")
    message.action = "fetchSteamApi"
    message.url = url
    chrome.runtime.sendMessage(message) { response ->
        chrome.runtime.lastError
        continuation.resume(response as? String)
    }
}
