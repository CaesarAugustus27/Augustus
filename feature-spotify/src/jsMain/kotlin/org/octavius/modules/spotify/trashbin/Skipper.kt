package org.octavius.modules.spotify.trashbin

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date
import kotlin.js.json

/**
 * Pomijanie utworów z kosza. Utwór z kosza przeskakujemy jednym skokiem do pierwszego spoza kosza w kolejce klienta.
 * Kolejka to okno najwyżej 80 utworów, kończy się z końcem kontekstu (playlisty, albumu), przy powtarzaniu zwykle też,
 * i dogania `songchange` po ułamku sekundy. Dlatego:
 * - dopóki nie dogoni, pokazuje stan sprzed zmiany; jeśli grający utwór jest w niej jeszcze przed nami, a za nim coś
 *   spoza kosza, skaczemy od razu, a na resztę decyzji czekamy na kolejkę, która go już ma jako bieżący;
 * - w oknie same utwory z kosza: skok na ostatni z nich i dalej przy jego `songchange`;
 * - puste okno: bez powtarzania to koniec, więc pauza; z powtarzaniem `next()`, które zawija na początek;
 * - utwór już minięty w tym ciągu skoków to pełne okrążenie, więc też pauza.
 */
object Skipper {
    private const val QUEUE_WAIT_MS = 3000
    private const val BACK_WINDOW_MS = 3000
    private const val SKIP_BACK_SELECTOR = "[data-testid='control-button-skip-back'], .main-skipBackButton-button"

    /** Numer zmiany; skok liczony na starej kolejce nie może przebić nowszej zmiany utworu. */
    private var skipRun = 0

    /** Utwory z kosza minięte od ostatniego utworu spoza kosza. */
    private val passed = mutableSetOf<String>()
    private var passedContext: String? = null
    private var backPressedAt = 0.0

    /** Nasłuch na całym dokumencie, bo Spotify potrafi przerenderować pasek odtwarzacza razem z przyciskiem. */
    fun listenForBack() {
        document.addEventListener("click", { event: Event ->
            if ((event.target as? Element)?.closest(SKIP_BACK_SELECTOR) != null) backPressedAt = Date.now()
        }, true)
    }

    fun onSongChange() {
        val context = Spicetify.Player.data?.context?.uri
        val newContext = context != passedContext
        if (newContext) {
            passed.clear()
            passedContext = context
        }

        // Powrót przyciskiem „Poprzedni” do utworu z kosza: użytkownik chce go posłuchać. Kliknięcie jest ważne przez
        // BACK_WINDOW_MS i tylko dla najbliższej zmiany utworu, więc cofnięcie na początek tego samego utworu nie
        // wyłącza pomijania na później.
        val wentBack = Date.now() - backPressedAt < BACK_WINDOW_MS
        backPressedAt = 0.0

        val item = Spicetify.Player.data?.item
        if (wentBack || item == null || !TrashStore.isTrashed(item)) {
            skipRun++
            passed.clear()
            return
        }
        trashbinScope.launch { skipTrashed(item, newContext) }
    }

    /** Przeskakuje grający utwór, jeśli to [uri] albo utwór wykonawcy [uri]. */
    fun skipIfPlaying(uri: String) {
        val item = Spicetify.Player.data?.item ?: return
        if (item.uri == uri || uri in item.artistUris()) trashbinScope.launch { skipTrashed(item, false) }
    }

    private suspend fun skipTrashed(item: PlayerTrack, newContext: Boolean) {
        val run = ++skipRun
        if (!passed.add(item.key())) return stopPlayback()

        val early = upcomingAfter(item, Spicetify.Player.origin.getQueue())?.firstOrNull { !TrashStore.isTrashed(it) }
        if (early != null) return skipTo(early)

        val upcoming = currentUpcoming(item, newContext)
        if (run != skipRun) return
        if (upcoming == null) return Spicetify.Player.next()

        val target = upcoming.firstOrNull { !TrashStore.isTrashed(it) }
        if (target != null) return skipTo(target)
        if (upcoming.isEmpty()) return if (Spicetify.Player.getRepeat() == 0) stopPlayback() else Spicetify.Player.next()

        // Ostatniego nie oznaczamy, bo na nim wylądujemy
        upcoming.dropLast(1).forEach { passed.add(it.key()) }
        skipTo(upcoming.last())
    }

    private suspend fun skipTo(track: PlayerTrack) {
        Spicetify.Player.origin.skipToNext(json("uri" to track.uri, "uid" to (track.uid ?: undefined))).await()
    }

    /** Utwory, które według kolejki są za [item]; `null`, jeśli kolejka [item] nie zawiera. */
    private fun upcomingAfter(item: PlayerTrack, queue: Queue): List<PlayerTrack>? {
        val upcoming = queue.queued.toList() + queue.nextUp
        if (queue.current?.key() == item.key()) return upcoming
        val index = upcoming.indexOfFirst { it.key() == item.key() }
        return if (index == -1) null else upcoming.drop(index + 1)
    }

    /**
     * Utwory za [item] według kolejki, która ma go już jako bieżący; `null`, jeśli kolejka nie nadąży.
     *
     * Czekamy na zdarzenie `queue_update`, a nie odpytujemy, bo Spotify w tle ma przyhamowane timery. Pusta kolejka
     * z `getQueue()` bywa chwilowa (zaraz po zmianie utworu), ale zdarzenie z pustą klient wysyła dopiero, gdy przez
     * 500 ms nic jej nie zastąpi, więc to ono potwierdza koniec. Wyjątkiem jest świeżo puszczony kontekst: klient
     * wczytuje go dłużej i zdąży ogłosić pustą kolejkę, zanim dojdzie reszta, więc tam koniec potwierdza dopiero
     * [QUEUE_WAIT_MS].
     */
    private suspend fun currentUpcoming(item: PlayerTrack, newContext: Boolean): List<PlayerTrack>? {
        val api = Spicetify.Player.origin
        val events = api.getEvents()
        fun upcomingOf(queue: Queue?): List<PlayerTrack>? =
            queue?.takeIf { it.current?.key() == item.key() }?.let { it.queued.toList() + it.nextUp }

        return suspendCoroutine { continuation ->
            var finished = false
            var timeout = 0
            lateinit var onUpdate: (QueueUpdate) -> Unit
            fun done(upcoming: List<PlayerTrack>?) {
                if (finished) return
                finished = true
                window.clearTimeout(timeout)
                events.removeListener("queue_update", onUpdate)
                continuation.resume(upcoming)
            }
            onUpdate = { event ->
                val upcoming = upcomingOf(event.data)
                if (upcoming != null && (upcoming.isNotEmpty() || !newContext)) done(upcoming)
            }
            // Zdarzenie mogło przyjść, zanim zaczęliśmy słuchać; kolejka, która przez QUEUE_WAIT_MS się nie zmieniła,
            // też jest pewna
            timeout = window.setTimeout({ done(upcomingOf(api.getQueue())) }, QUEUE_WAIT_MS)
            events.addListener("queue_update", onUpdate)

            val upcoming = upcomingOf(api.getQueue())
            if (!upcoming.isNullOrEmpty()) done(upcoming)
        }
    }

    private fun stopPlayback() {
        passed.clear()
        Spicetify.Player.pause()
        Spicetify.showNotification("Dalej są już tylko utwory z kosza")
    }
}
