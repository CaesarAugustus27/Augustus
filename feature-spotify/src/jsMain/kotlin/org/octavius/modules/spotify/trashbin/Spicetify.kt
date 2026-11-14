package org.octavius.modules.spotify.trashbin

import kotlin.js.Promise

/**
 * Część globalnego `Spicetify`, której używa Trashbin. Odtwarzacz, kolejka i zapytania GraphQL to nieudokumentowane
 * API klienta Spotify; kształty są takie, jakie zwraca klient.
 */
external object Spicetify {
    val Player: SpicetifyPlayer
    val LocalStorage: SpicetifyLocalStorage
    val GraphQL: SpicetifyGraphQL?
    val PopupModal: SpicetifyPopupModal
    val React: dynamic
    val ReactDOM: dynamic

    /** Funkcja tippy.js albo `undefined`. */
    val Tippy: dynamic
    val TippyProps: dynamic

    fun showNotification(text: String, isError: Boolean = definedExternally)

    object ContextMenu {
        /** Pozycja menu kontekstowego; [shouldAdd] decyduje przy każdym otwarciu, czy się pokazuje. */
        class Item(
            name: String,
            onClick: (uris: Array<String>) -> Unit,
            shouldAdd: (uris: Array<String>) -> Boolean,
            icon: String
        ) {
            var name: String
            fun register()
        }
    }

    object Menu {
        /** Pozycja menu profilu. */
        class Item(name: String, isEnabled: Boolean, onClick: (self: dynamic) -> Unit, icon: String) {
            fun register()
        }
    }
}

external interface SpicetifyPlayer {
    val data: PlayerState?
    val origin: PlayerApi
    fun next()
    fun pause()

    /** 0 bez powtarzania, 1 kontekst, 2 utwór. */
    fun getRepeat(): Int
    fun addEventListener(type: String, callback: (event: dynamic) -> Unit)
}

external interface PlayerState {
    val item: PlayerTrack?
    val context: PlayerContext?
}

external interface PlayerContext {
    val uri: String?
}

/** Utwór odtwarzacza albo kolejki; `metadata` trzyma wykonawców pod `artist_uri`, `artist_uri:1`, `artist_uri:2`, … */
external interface PlayerTrack {
    val uri: String
    val uid: String?
    val metadata: dynamic
}

external interface PlayerApi {
    /** Okno najwyżej 80 utworów, ucięte na końcu kontekstu. */
    fun getQueue(): Queue
    fun getEvents(): PlayerEvents

    /** Przeskakuje na wskazany utwór z kolejki (`{uri, uid}`). */
    fun skipToNext(track: dynamic): Promise<Unit>
}

external interface Queue {
    val current: PlayerTrack?
    val queued: Array<PlayerTrack>
    val nextUp: Array<PlayerTrack>
}

external interface PlayerEvents {
    fun addListener(type: String, listener: (event: QueueUpdate) -> Unit)
    fun removeListener(type: String, listener: (event: QueueUpdate) -> Unit)
}

/** Zdarzenie `queue_update`. */
external interface QueueUpdate {
    val data: Queue?
}

external interface SpicetifyLocalStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

external interface SpicetifyGraphQL {
    /** Zapytania, których używa sam klient, po nazwie. */
    val Definitions: dynamic
    fun Request(definition: dynamic, variables: dynamic): Promise<dynamic>
}

external interface SpicetifyPopupModal {
    /** `{title, content}`, gdzie `content` to element DOM. */
    fun display(options: dynamic)
}

/** Zbiór z JS-a. Porównuje obiekty po tożsamości i nic do nich nie dopisuje, w przeciwieństwie do `HashSet`. */
@JsName("Set")
external class JsSet<T> {
    fun add(value: T): JsSet<T>
    fun has(value: T): Boolean
    fun forEach(callback: (value: T) -> Unit)
}

private val ARTIST_KEY = Regex("^artist_uri(:\\d+)?$")

/** URI wykonawców z `metadata`. */
fun PlayerTrack.artistUris(): List<String> {
    val metadata = metadata ?: return emptyList()
    return objectKeys(metadata).filter { ARTIST_KEY.matches(it) }.map { metadata[it] as String }
}

/** Odróżnia powtórzenia tego samego utworu w kontekście; utwór bez uid rozpoznaje po URI. */
fun PlayerTrack.key(): String = uid?.takeIf { it.isNotEmpty() } ?: uri

fun objectKeys(value: dynamic): Array<String> = js("Object").keys(value).unsafeCast<Array<String>>()
