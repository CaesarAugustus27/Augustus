package org.octavius.modules.spotify.spicetify.trash

import org.octavius.modules.spotify.spicetify.JsSet
import org.octavius.modules.spotify.spicetify.objectKeys
import org.w3c.dom.Element

/** URI utworu w wierszu tracklisty. DOM go nie zawiera, więc wyciągamy go z danych Reacta podpiętych pod elementy wiersza. */
object RowUri {
    private const val TRACK_PREFIX = "spotify:track:"

    fun find(row: Element): String? {
        val candidates = listOfNotNull(
            row.querySelector("[role=\"presentation\"]"),
            row.querySelector("[role=\"gridcell\"] button"),
            row
        )

        // 1) propsy przypięte bezpośrednio do elementu DOM
        for (element in candidates) {
            val key = reactKey(element, "__reactProps\$") ?: continue
            trackUri(firstUri(element.asDynamic()[key], 10, JsSet()))?.let { return it }
        }

        // 2) drzewo komponentów (fiber): idziemy w górę do 20 poziomów
        for (element in candidates) {
            val key = reactKey(element, "__reactFiber\$") ?: continue
            var fiber: dynamic = element.asDynamic()[key]
            var level = 0
            while (fiber != null && level < 20) {
                val props: dynamic = fiber.memoizedProps ?: fiber.pendingProps
                if (props != null && jsTypeOf(props) == "object") fromProps(props)?.let { return it }
                fiber = fiber["return"]
                level++
            }
        }
        return null
    }

    private fun reactKey(element: Element, prefix: String): String? = objectKeys(element).firstOrNull { it.startsWith(prefix) }

    /** Pierwszy klucz `uri` w zagnieżdżonym obiekcie, najwyżej [depth] poziomów, z ochroną przed cyklami. */
    private fun firstUri(value: dynamic, depth: Int, seen: JsSet<Any>): dynamic {
        if (depth <= 0 || value == null || jsTypeOf(value) != "object" || seen.has(value)) return undefined
        seen.add(value)
        if (value.uri !== undefined) return value.uri
        for (key in objectKeys(value)) {
            val found = firstUri(value[key], depth - 1, seen)
            if (found !== undefined) return found
        }
        return undefined
    }

    /** URI utworu z propsów komponentu: `props.uri`, `props.x.uri` albo `props.x.y.uri`. */
    private fun fromProps(props: dynamic): String? {
        trackUri(props.uri)?.let { return it }
        for (key in objectKeys(props)) {
            val value = props[key]
            if (skipped(key) || !isPlainObject(value)) continue
            trackUri(value.uri)?.let { return it }
            for (key2 in objectKeys(value)) {
                val value2 = value[key2]
                if (!skipped(key2) && isPlainObject(value2)) trackUri(value2.uri)?.let { return it }
            }
        }
        return null
    }

    private fun skipped(key: String) = key == "children" || key == "ref" || key.startsWith("on")

    private fun isPlainObject(value: dynamic): Boolean =
        value != null && jsTypeOf(value) == "object" && !(js("Array").isArray(value) as Boolean)

    private fun trackUri(value: dynamic): String? = (value as? String)?.takeIf { it.startsWith(TRACK_PREFIX) }
}
