package org.octavius.modules.spotify.spicetify

import io.github.octaviusframework.i18n.core.OctaviusI18n
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.octavius.modules.spotify.spicetify.liked.LikedCopy
import org.octavius.modules.spotify.spicetify.trash.Settings
import org.octavius.modules.spotify.spicetify.trash.Skipper
import org.octavius.modules.spotify.spicetify.trash.TrashButtons
import org.octavius.modules.spotify.spicetify.trash.TrashCopy
import org.octavius.modules.spotify.spicetify.trash.TrashMenu

/**
 * Korutyny rozszerzenia. `Unconfined`, bo tak działa `async` w JS: korutyna biegnie od razu do pierwszego zawieszenia
 * i wraca w callbacku obietnicy albo timera. Pomijanie na tym polega, bo numer skoku musi wzrosnąć przy samym
 * `songchange`.
 */
val spicetifyScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> console.error(e) })

/** Spicetify wystawia swoje API dopiero po załadowaniu klienta. */
private val REQUIRED_API = listOf("LocalStorage", "React", "ReactDOM", "ContextMenu", "Menu", "PopupModal", "Platform")

/**
 * Rozszerzenie Spicetify Augustusa (augustus.js): kosz na utwory i wykonawców, których Spotify ma nie grać, oraz
 * kopie kosza i polubionych w Augustusie.
 */
fun main() {
    spicetifyScope.launch {
        while (!ready()) delay(500)
        start()
    }
}

private fun ready(): Boolean {
    val spicetify = Spicetify.asDynamic()
    return spicetify.Player?.data != null && REQUIRED_API.all { spicetify[it] != null }
}

private fun start() {
    // Napisy w języku klienta, czytanym raz przy starcie; język bez tłumaczeń dostaje angielskie
    OctaviusI18n.currentLanguage = Spicetify.Locale?.getLocale() ?: "en"

    Settings.register()
    TrashMenu.register()
    Skipper.listenForBack()
    Spicetify.Player.addEventListener("songchange") { onSongChange() }
    TrashButtons.start()
    onSongChange()

    // Synchronizacja z opóźnieniem, żeby nie spowalniać ładowania Spotify (opisy idą zapytaniami GraphQL)
    window.setTimeout({
        TrashCopy.sync()
        LikedCopy.start()
    }, 5000)
}

private fun onSongChange() {
    TrashButtons.syncPlaybar()
    Skipper.onSongChange()
}
