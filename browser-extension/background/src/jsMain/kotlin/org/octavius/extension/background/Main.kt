package org.octavius.extension.background

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.octavius.extension.util.WebRequestDetails
import org.octavius.extension.util.chrome
import org.w3c.dom.url.URL
import kotlin.js.Promise

private const val NOVEL_UPDATES_LIST_MOVE_URL = "http://localhost:8080/api/asian-media/novelupdates/list-move"
private const val STEAM_IGNORE_URL = "http://localhost:8080/api/games/steam/ignore"
private const val STEAM_API_URL = "https://api.steampowered.com/"
private const val PENDING_KEY = "pendingRequests"

external fun fetch(input: String, init: dynamic): Promise<dynamic>

// Zdarzenia przychodzą seriami, a chrome.storage nie ma odczytu z zapisem w jednym kroku - kolejkę
// zmienia i wysyła jedna korutyna naraz.
private val queueLock = Mutex()

/**
 * Tło wtyczki: przekazuje aplikacji zmiany zrobione na stronach, tak żeby Augustus je powtórzył.
 * - Przeniesienia serii między listami lektur na NovelUpdates. Każde przeniesienie - z series findera,
 *   strony serii czy strony listy - idzie jednym zapytaniem do `updatelist.php`, więc wystarczy
 *   obserwować ten adres; do NovelUpdates nie wysyłamy nic.
 * - Gry zignorowane na SteamDB. Te podsłuchuje content script (patrz `listenToSteamDbIgnores`) i
 *   przysyła tutaj gotowy opis gry.
 *
 * Zmiana, której nie udało się wysłać, bo Augustus nie działa, czeka w `chrome.storage` i idzie przy
 * następnej zmianie albo następnym starcie tła.
 *
 * Do tego tło wysyła za content script zapytania do API Steama (opisy gier na listach SteamDB): content
 * script podlega CORS strony, a API Steama nie przepuszcza zapytań ze steamdb.info.
 */
@OptIn(DelicateCoroutinesApi::class)
fun main() {
    // Nasłuchy muszą się zarejestrować przy pierwszym przebiegu skryptu - inaczej przepada zdarzenie,
    // które właśnie obudziło service worker.
    chrome.webRequest.onCompleted.addListener(::onUpdateList, js("({ urls: ['https://www.novelupdates.com/updatelist.php*'] })"))
    chrome.runtime.onMessage.addListener { message, sender, sendResponse ->
        when (message.action) {
            "mirrorSteamIgnore" -> {
                val tabId = sender.tab?.id
                if (tabId != null) send(STEAM_IGNORE_URL, message.ignore as String, tabId)
                false
            }
            // true: odpowiedź przyjdzie później, więc Chrome ma trzymać sendResponse
            "fetchSteamApi" -> {
                relaySteamApi(message.url as String, sendResponse)
                true
            }
            else -> false
        }
    }
    GlobalScope.launch { queueLock.withLock { flush() } }
}

/** Odsyła treść odpowiedzi API Steama spod [url] albo `null`, gdy zapytanie się nie udało. */
@OptIn(DelicateCoroutinesApi::class)
private fun relaySteamApi(url: String, sendResponse: (dynamic) -> Unit) {
    if (!url.startsWith(STEAM_API_URL)) {
        sendResponse(null)
        return
    }
    GlobalScope.launch {
        val body = try {
            val response: dynamic = fetch(url, js("({})")).await()
            if (response.ok as Boolean) response.text().unsafeCast<Promise<String>>().await() else null
        } catch (e: Throwable) {
            null
        }
        sendResponse(body)
    }
}

private fun onUpdateList(details: WebRequestDetails) {
    if (details.statusCode != 200 || details.tabId < 0) return
    val params = URL(details.url).searchParams
    // Między listami seria tylko się przenosi ("move"); cokolwiek innego pomijamy.
    if (params.get("act") != "move") return
    val sid = params.get("sid") ?: return
    val listId = params.get("lid")?.toIntOrNull() ?: return

    // Tytuł i język zna tylko strona, na której kliknięto.
    val question: dynamic = js("({})")
    question.action = "describeNovelUpdatesListMove"
    question.sid = sid
    question.listId = listId
    chrome.tabs.sendMessage(details.tabId, question) { response ->
        ignoreLastError()
        val move = response as? String
        if (move == null) {
            toast(details.tabId, "nie rozpoznałem serii $sid na tej stronie, nic nie wysłano", false)
            return@sendMessage
        }
        send(NOVEL_UPDATES_LIST_MOVE_URL, move, details.tabId)
    }
}

/** Dopisuje zmianę do kolejki i wysyła kolejkę; komunikat o wyniku trafia na kartę [tabId]. */
@OptIn(DelicateCoroutinesApi::class)
private fun send(url: String, body: String, tabId: Int) {
    GlobalScope.launch {
        queueLock.withLock {
            val request: dynamic = js("({})")
            request.url = url
            request.body = body
            request.tabId = tabId
            save(pending().toMutableList().apply { add(request) })
            val left = flush()
            if (left > 0) toast(tabId, "aplikacja nie odpowiada - czeka w kolejce ($left), pójdzie przy następnej zmianie", false)
        }
    }
}

/** Wysyła kolejkę po kolei i zwraca, ile zostało; zatrzymuje się na pierwszym braku połączenia. */
private suspend fun flush(): Int {
    val queue = pending().toMutableList()
    while (queue.isNotEmpty()) {
        val item = queue.first()
        val response = try {
            fetch(item.url as String, jsonPost(item.body as String)).await()
        } catch (e: Throwable) {
            return queue.size
        }
        queue.removeAt(0)
        save(queue)
        // Odpowiedź z błędem nie wraca do kolejki - ponawianie jej by niczego nie zmieniło, a
        // zatkałoby kolejkę za nią.
        if (response.ok as Boolean) {
            val body = response.json().unsafeCast<Promise<dynamic>>().await()
            toast(item.tabId as Int, body.message as String, body.saved as Boolean)
        } else {
            toast(item.tabId as Int, "aplikacja odpowiedziała ${response.status}, nic nie zostało zapisane", false)
        }
    }
    return 0
}

private suspend fun pending(): Array<dynamic> {
    // Najpierw do zmiennej: `get(...).await()[PENDING_KEY]` w jednym wyrażeniu Kotlin/JS kompiluje bez
    // sprawdzenia, czy await się zawiesił - funkcja wracała z pustą kolejką, zanim storage odpowiedział,
    // a potem wznowienie zakończonej już korutyny wywalało całe tło.
    val stored: dynamic = chrome.storage.local.get(PENDING_KEY).await()
    return stored[PENDING_KEY] as? Array<dynamic> ?: emptyArray()
}

private suspend fun save(queue: List<dynamic>) {
    val items: dynamic = js("({})")
    items[PENDING_KEY] = queue.toTypedArray()
    chrome.storage.local.set(items).await()
}

private fun jsonPost(body: String): dynamic {
    val init: dynamic = js("({})")
    init.method = "POST"
    init.headers = js("({ 'Content-Type': 'application/json' })")
    init.body = body
    return init
}

// Karta, z której przyszła zmiana, mogła zostać zamknięta, zanim kolejka doszła do niej.
private fun toast(tabId: Int, text: String, saved: Boolean) {
    val message: dynamic = js("({})")
    message.action = "showAugustusToast"
    message.text = text
    message.saved = saved
    chrome.tabs.sendMessage(tabId, message) { ignoreLastError() }
}

// Odczytany w callbacku błąd nie jest wypisywany przez Chrome jako "Unchecked runtime.lastError".
private fun ignoreLastError() {
    chrome.runtime.lastError
}
