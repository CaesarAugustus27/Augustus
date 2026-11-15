package org.octavius.modules.games.parser

import kotlinx.browser.document
import org.octavius.modules.games.model.SteamIgnore
import org.w3c.dom.Element
import org.w3c.dom.asList

/**
 * Odczyt gier ze stron SteamDB.
 */
object SteamDbParser {

    /**
     * Opisuje grę, którą użytkownik właśnie zignorował, z tego, co widać na bieżącej stronie: nagłówka
     * strony gry albo kafelka czy wiersza na liście (wyprzedaże, wykresy, wyszukiwarka). `null`, gdy tej
     * gry na stronie nie ma.
     */
    fun ignoredGame(appId: Int): SteamIgnore? {
        val name = appPageName(appId) ?: listName(appId) ?: return null
        return SteamIgnore(appId, name)
    }

    /**
     * Wiersze tabel z grami (wyprzedaże, wykresy, wyszukiwarka) razem z linkiem, z którego [ignoredGame]
     * czyta nazwę. Wiersz bez takiego linku pomija.
     */
    fun gameRows(): List<SteamDbGameRow> =
        document.querySelectorAll("tr.app[data-appid]").asList().mapNotNull { node ->
            val row = node as Element
            val appId = row.getAttribute("data-appid")?.toIntOrNull() ?: return@mapNotNull null
            nameLink(row, appId)?.let { SteamDbGameRow(appId, it) }
        }

    // Na stronie gry linki do jej podstron (/app/<id>/charts/ itd.) mają podpisy w rodzaju "Charts",
    // więc nazwę bierzemy tylko z nagłówka.
    private fun appPageName(appId: Int): String? =
        document.querySelector(".scope-app[data-appid='$appId'] h1[itemprop=name]")?.textContent?.normalized()

    private fun listName(appId: Int): String? =
        document.querySelectorAll(".app[data-appid='$appId']").asList().firstNotNullOfOrNull { node ->
            nameLink(node as Element, appId)?.textContent?.normalized()
        }

    // Kafelek to sam link z nazwą, a w wierszu tabeli pierwszy link do gry jest obrazkiem bez tekstu,
    // dlatego pierwszy link z tekstem.
    private fun nameLink(element: Element, appId: Int): Element? {
        val links = listOf(element).filter { it.tagName == "A" } +
            element.querySelectorAll("a[href^='/app/$appId/']").asList().map { it as Element }
        return links.firstOrNull { it.textContent?.normalized() != null }
    }

    private fun String.normalized(): String? = replace(Regex("\\s+"), " ").trim().ifEmpty { null }
}

/** Wiersz gry [appId] na liście SteamDB; [nameLink] niesie jej nazwę. */
class SteamDbGameRow(val appId: Int, val nameLink: Element)
