package org.octavius.modules.spotify.spicetify.trash

import kotlinx.browser.document
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.octavius.modules.spotify.localization.SpotifyTr
import org.octavius.modules.spotify.spicetify.ClientStorage
import org.octavius.modules.spotify.spicetify.JsSet
import org.octavius.modules.spotify.spicetify.Spicetify
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.MutationRecord
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set
import kotlin.js.json

/**
 * Przycisk kosza w wierszach tracklisty i na pasku odtwarzacza. Spotify przy aktualizacjach zmienia zahashowane klasy
 * CSS, a css-map Spicetify nie zawsze nadąża (bez wpisu dla paska `Spicetify.Playbar.Widget` nie ma się do czego
 * przyczepić), dlatego oba miejsca znajdujemy po atrybutach:
 * - wiersz: `[role="row"]` → ostatnia komórka `[role="gridcell"]`, czyli sekcja z serduszkiem, czasem trwania i „...”
 *   (klasę `.main-trackList-rowSectionEnd` bierzemy, gdy css-map ją zna);
 * - pasek: `now-playing-widget(-v2)` → przycisk dodawania do playlisty (`buttonTertiary` z `aria-checked`).
 */
object TrashButtons {
    private const val WIDGET_KEY = "TrashbinWidgetIcon"
    private const val NOW_PLAYING_SELECTOR = "[data-testid=\"now-playing-widget\"], [data-testid=\"now-playing-widget-v2\"]"
    private const val PLAYBAR_ANCHOR_SELECTOR = "button[data-encore-id=\"buttonTertiary\"][aria-checked]"
    private const val ROW_SELECTOR = "[role=\"row\"]"
    private const val WRAPPER_CLASS = "trashbin-wrapper"
    private const val STYLE = """
        .trashbin-button { color: var(--text-subdued) !important; opacity: .7 !important; }
        .trashbin-button:hover { opacity: 1 !important; }
        .trashbin-button.trashbin-active { color: var(--text-negative) !important; opacity: 1 !important; visibility: visible !important; }
        .trashbin-in-row > .trashbin-button { margin-right: 12px !important; }
    """

    private val React = Spicetify.React
    private val ReactDOM = Spicetify.ReactDOM
    private val button: dynamic = React.memo { props: dynamic -> renderButton(props) }

    /** Czy przycisk ma być też na pasku odtwarzacza. */
    var playbarEnabled: Boolean = (ClientStorage.read(WIDGET_KEY) as? JsonPrimitive)?.booleanOrNull ?: true
        private set

    fun setPlaybarEnabled(enabled: Boolean) {
        playbarEnabled = enabled
        ClientStorage.write(WIDGET_KEY, JsonPrimitive(enabled))
        syncPlaybar()
    }

    fun start() {
        val style = document.createElement("style")
        style.textContent = STYLE
        document.head!!.appendChild(style)

        MutationObserver { mutations, _ -> onMutations(mutations) }
            .observe(document.body!!, MutationObserverInit(childList = true, subtree = true))
        document.querySelectorAll(ROW_SELECTOR).asList().forEach { updateRow(it as Element) }
        syncPlaybar()
    }

    /** Przycisk na pasku dla grającego utworu, przed przyciskiem dodawania do playlisty. */
    fun syncPlaybar() {
        val existing = document.querySelector(".trashbin-playbar") as HTMLElement?
        val uri = Spicetify.Player.data?.item?.uri
        val anchor = document.querySelector(NOW_PLAYING_SELECTOR)?.querySelector(PLAYBAR_ANCHOR_SELECTOR)
        if (!playbarEnabled || uri == null || !uri.startsWith("spotify:track:") || anchor == null) {
            existing?.let(::unmount)
            return
        }
        if (existing?.nextElementSibling !== anchor) {
            existing?.let(::unmount)
            mount(anchor.parentElement!!, anchor, anchor, uri, "trashbin-playbar")
        } else if (existing.dataset["uri"] != uri) {
            render(existing, anchor, uri)
        }
    }

    private fun onMutations(mutations: Array<MutationRecord>) {
        val rows = JsSet<Element>()
        val removedWrappers = mutableListOf<Element>()

        for (mutation in mutations) {
            for (node in mutation.removedNodes.asList()) {
                if (node.nodeType != Node.ELEMENT_NODE) continue
                node as Element
                if (node.matches(".$WRAPPER_CLASS")) removedWrappers.add(node)
                else node.querySelectorAll(".$WRAPPER_CLASS").asList().forEach { removedWrappers.add(it as Element) }
            }

            for (node in mutation.addedNodes.asList()) {
                // Pomijamy własne zmiany (montowanie naszych przycisków)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                node as Element
                if (node.closest(".$WRAPPER_CLASS") != null) continue

                if (node.matches(ROW_SELECTOR)) {
                    rows.add(node)
                    continue
                }
                val nested = node.querySelectorAll(ROW_SELECTOR)
                if (nested.length > 0) nested.asList().forEach { rows.add(it as Element) }
                // Treść doładowana do istniejącego wiersza
                else node.closest(ROW_SELECTOR)?.let { rows.add(it) }
            }
        }

        // Wiersze, które Spotify wyrzuciło z DOM (np. przewijanie długiej playlisty): sprzątamy ich roota Reacta.
        // isConnected, bo przeniesiony wiersz też daje „removed”, a wtedy przycisk ma zostać.
        for (wrapper in removedWrappers) {
            if (!wrapper.isConnected && wrapper.asDynamic()._trashbinRoot != null) unmount(wrapper as HTMLElement)
        }
        rows.forEach(::updateRow)
        // Pasek też potrafi się przerenderować razem z przyciskiem, do którego się przyczepiamy
        syncPlaybar()
    }

    private fun updateRow(row: Element) {
        val cell = rowEndCell(row) ?: return
        val existing = cell.querySelector(":scope > .trashbin-in-row") as HTMLElement?
        val uri = RowUri.find(row)
        if (existing != null && existing.dataset["uri"] == uri) return

        // Wirtualizowana lista potrafi podmienić utwór w tym samym wierszu, wtedy montujemy od nowa
        existing?.let(::unmount)
        if (uri == null) return

        // Wstawiamy przed serduszkiem (pomijając przełącznik „Ukryj na tej playliście” i ostatni przycisk „...”).
        // Utwór wykluczony z albumu albo playlisty nie ma serduszka, wtedy kosz idzie tuż za przełącznik, czyli w to
        // samo miejsce.
        val heart = cell.querySelector(":scope > button:not([role='switch']):not(:last-child)")
        val hideSwitch = cell.querySelector(":scope > button[role='switch']")
        val template = heart ?: hideSwitch ?: return
        mount(cell, heart ?: hideSwitch?.nextSibling, template, uri, "trashbin-in-row")
    }

    private fun rowEndCell(row: Element): Element? {
        row.querySelector(".main-trackList-rowSectionEnd")?.let { return it }

        val cells = row.querySelectorAll("[role=\"gridcell\"]")
        // Np. wiersze Biblioteki w sidebarze mają jedną komórkę
        if (cells.length < 2) return null
        val last = cells.item(cells.length - 1) as Element
        // Zwykły wiersz ma przycisk „...”, a utwór wykluczony z albumu albo playlisty ma tylko przełącznik „Pokaż na tym
        // albumie”
        val hasMenu = last.querySelector(":scope > button[aria-haspopup=\"menu\"], :scope > button[role=\"switch\"]") != null
        return if (hasMenu) last else null
    }

    /** Nowy wrapper z przyciskiem przed [before]. Klasy kopiujemy z natywnego przycisku [template], żeby wyglądał jak on. */
    private fun mount(parent: Node, before: Node?, template: Element, uri: String, placeClass: String) {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.className = "$WRAPPER_CLASS $placeClass"
        wrapper.style.display = "contents"
        parent.insertBefore(wrapper, before)
        if (jsTypeOf(ReactDOM.createRoot) == "function") wrapper.asDynamic()._trashbinRoot = ReactDOM.createRoot(wrapper)
        render(wrapper, template, uri)
    }

    private fun render(wrapper: HTMLElement, template: Element, uri: String) {
        wrapper.dataset["uri"] = uri
        val props = json(
            "uri" to uri,
            "className" to template.className,
            "iconClassName" to (template.querySelector(":scope > span")?.className ?: "")
        )
        val element = React.createElement(button, props)
        val root = wrapper.asDynamic()._trashbinRoot
        if (root != null) root.render(element) else ReactDOM.render(element, wrapper)
    }

    private fun unmount(wrapper: HTMLElement) {
        val root = wrapper.asDynamic()._trashbinRoot
        if (root != null) root.unmount()
        else if (jsTypeOf(ReactDOM.unmountComponentAtNode) == "function") ReactDOM.unmountComponentAtNode(wrapper)
        wrapper.asDynamic()._trashbinRoot = null
        wrapper.remove()
    }

    /** Komponent przycisku; odświeża się po [TrashStore.CHANGE_EVENT]. */
    private fun renderButton(props: dynamic): dynamic {
        val uri = props.uri as String
        val state = React.useState { uri in TrashStore }
        val trashed = state[0] as Boolean
        val setTrashed = state[1]
        val ref = React.useRef(null)
        val label = if (trashed) SpotifyTr.Trash.remove() else SpotifyTr.Trash.add()

        React.useEffect({
            val update: (Event) -> Unit = { setTrashed(uri in TrashStore) }
            setTrashed(uri in TrashStore)
            document.addEventListener(TrashStore.CHANGE_EVENT, update)
            val cleanup: () -> Unit = { document.removeEventListener(TrashStore.CHANGE_EVENT, update) }
            cleanup
        }, arrayOf(uri))

        React.useEffect({
            val element = ref.current
            if (element == null || jsTypeOf(Spicetify.Tippy) != "function") {
                undefined
            } else {
                val options = js("Object").assign(json(), Spicetify.TippyProps, json("hideOnClick" to true, "content" to label))
                val tooltip = Spicetify.Tippy(element, options)
                val destroy: () -> Unit = { tooltip.destroy() }
                destroy
            }
        }, arrayOf(label))

        val icon = React.createElement(
            "span",
            json(
                "className" to props.iconClassName,
                "aria-hidden" to "true",
                "dangerouslySetInnerHTML" to json("__html" to TRASH_ICON)
            )
        )
        val onClick: (dynamic) -> Unit = { event ->
            event.stopPropagation()
            TrashStore.toggle(uri)
        }
        return React.createElement(
            "button",
            json(
                "ref" to ref,
                "className" to "${props.className} trashbin-button${if (trashed) " trashbin-active" else ""}",
                "aria-label" to label,
                "tabIndex" to -1,
                "onClick" to onClick
            ),
            icon
        )
    }
}
