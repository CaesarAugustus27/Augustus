package org.octavius.modules.spotify.trashbin

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Korutyny rozszerzenia. `Unconfined`, bo tak działa `async` w JS: korutyna biegnie od razu do pierwszego zawieszenia
 * i wraca w callbacku obietnicy albo timera. Pomijanie na tym polega, bo numer skoku musi wzrosnąć przy samym
 * `songchange`.
 */
val trashbinScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> console.error(e) })

/** Spicetify wystawia swoje API dopiero po załadowaniu klienta. */
private val REQUIRED_API = listOf("LocalStorage", "React", "ReactDOM", "ContextMenu", "Menu", "PopupModal")

/** Rozszerzenie Spicetify „Trashbin”: kosz na utwory i wykonawców, których Spotify ma nie grać, z kopią w Augustusie. */
fun main() {
    trashbinScope.launch {
        while (!ready()) delay(500)
        start()
    }
}

private fun ready(): Boolean {
    val spicetify = Spicetify.asDynamic()
    return spicetify.Player?.data != null && REQUIRED_API.all { spicetify[it] != null }
}

private fun start() {
    Settings.register()
    TrashMenu.register()
    Skipper.listenForBack()
    Spicetify.Player.addEventListener("songchange") { onSongChange() }
    TrashButtons.start()
    onSongChange()

    // Synchronizacja z opóźnieniem, żeby nie spowalniać ładowania Spotify (opisy idą zapytaniami GraphQL)
    window.setTimeout({ AugustusCopy.sync() }, 5000)
}

private fun onSongChange() {
    TrashButtons.syncPlaybar()
    Skipper.onSongChange()
}
