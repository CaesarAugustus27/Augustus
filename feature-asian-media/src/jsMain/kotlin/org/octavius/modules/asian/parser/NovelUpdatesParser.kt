package org.octavius.modules.asian.parser

import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.octavius.shared.contract.ParsedData
import org.octavius.shared.contract.Parser
import org.octavius.domain.asian.PublicationLanguage
import org.octavius.domain.asian.PublicationType
import org.octavius.domain.asian.SourceSite
import org.octavius.modules.asian.model.AsianPublicationData
import org.octavius.modules.asian.model.ExternalId
import org.octavius.modules.asian.model.NovelUpdatesListMove
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.get

/**
 * Parser do wyciągania danych ze strony NovelUpdates.
 * Używa selektorów CSS do znalezienia odpowiednich elementów.
 */
object NovelUpdatesParser : Parser<AsianPublicationData> {

    override val moduleId: String = AsianPublicationData.MODULE_ID

    override fun canParse(url: String): Boolean {
        return "novelupdates.com/series/" in url
    }

    override fun serialize(data: AsianPublicationData): String {
        return Json.encodeToString(AsianPublicationData.serializer(), data)
    }

    override suspend fun parse(): ParsedData {
        // Główny tytuł jest w divie z klasą .seriestitlenu
        val mainTitle = document.querySelector(".seriestitlenu")?.textContent?.trim() ?: ""

        // Tytuły alternatywne znajdują się w divie o id #editassociated.
        // Są oddzielone tagami <br>, więc pobieramy innerHTML i dzielimy go.
        val associatedNamesDiv = document.querySelector("#editassociated") as? HTMLElement
        val associatedTitles = associatedNamesDiv?.innerHTML
            ?.split("<br>")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()

        // Łączymy i filtrujemy tytuły, aby zostawić tylko te z alfabetem łacińskim
        val allTitles = (listOf(mainTitle) + associatedTitles)
            .filter { it.isPrimarilyLatinScript() }
            .distinct()

        // Typ publikacji (Web Novel, Light Novel etc.), bezpieczny domyślny typ dla tej strony
        val detectedType = seriesPageType() ?: PublicationType.WebNovel

        // Język oryginału, np. (CN)
        val languageString = document.querySelector("#showtype span")?.textContent
            ?.trim()
            ?.replace(Regex("[()]"), "") // Usuwamy nawiasy

        val language = when (languageString) {
            "CN" -> PublicationLanguage.Chinese
            "KR" -> PublicationLanguage.Korean
            "JP" -> PublicationLanguage.Japanese
            // Domyślnie chiński, ponieważ jest najczęstszy na NovelUpdates
            else -> PublicationLanguage.Chinese
        }

        // sid serii - ten sam, który podsłuch list i import listy zapisują w title_external_ids.
        val sid = (document.querySelector("#mypostid") as? HTMLInputElement)?.value?.takeIf { it.isNotBlank() }

        return AsianPublicationData(
            source = "NovelUpdates",
            titles = allTitles,
            type = detectedType,
            language = language,
            externalId = sid?.let { ExternalId(SourceSite.NovelUpdates, it) }
        )
    }

    /**
     * Opisuje serię, którą użytkownik właśnie przeniósł na liście lektur, z tego, co widać na bieżącej
     * stronie: wiersza w series finderze, strony serii albo wiersza na stronie listy. `null`, gdy tej
     * serii na stronie nie ma.
     */
    fun listMove(sid: String, listId: Int): NovelUpdatesListMove? {
        val finderRow = document.getElementById("sid$sid")?.closest(".search_main_box_nu")
        if (finderRow != null) {
            val title = finderRow.querySelector(".search_title a")?.textContent?.trim() ?: return null
            val org = finderRow.querySelectorAll("span").asList()
                .firstNotNullOfOrNull { Regex("^org([a-z]{2})$").find((it as Element).className)?.groupValues?.get(1) }
            return NovelUpdatesListMove(sid, listId, title, org)
        }

        if ((document.querySelector("#mypostid") as? HTMLInputElement)?.value == sid) {
            val title = document.querySelector(".seriestitlenu")?.textContent?.trim() ?: return null
            // Język jest w nawiasie obok typu, np. "(CN)".
            val org = document.querySelector("#showtype span")?.textContent?.trim()?.removeSurrounding("(", ")")?.lowercase()
            return NovelUpdatesListMove(sid, listId, title, org, seriesPageType())
        }

        val listRow = document.querySelector("tr.rl_links[data-sid='$sid']") as? HTMLElement
        val listTitle = listRow?.dataset?.get("title")?.trim()
        if (!listTitle.isNullOrEmpty()) return NovelUpdatesListMove(sid, listId, listTitle)

        return null
    }

    private fun seriesPageType(): PublicationType? =
        when (document.querySelector("#showtype a")?.textContent?.trim()?.uppercase()) {
            "WEB NOVEL" -> PublicationType.WebNovel
            "LIGHT NOVEL" -> PublicationType.LightNovel
            "PUBLISHED NOVEL" -> PublicationType.PublishedNovel
            else -> null
        }

    /**
     * Zwraca `true`, jeśli ciąg znaków składa się głównie ze znaków łacińskich,
     * cyfr i standardowej interpunkcji. Wyklucza większość popularnych
     * niełacińskich skryptów.
     */
    private fun String.isPrimarilyLatinScript(): Boolean {
        if (this.isBlank()) return false
        return this.none { char ->
            when (char.code) {
                // Azja Wschodnia
                in 0x3040..0x309F -> true // Hiragana
                in 0x30A0..0x30FF -> true // Katakana
                in 0x4E00..0x9FBF -> true // Kanji / Hanzi (chińskie, japońskie)
                in 0xAC00..0xD7AF -> true // Hangul (koreańskie)
                // Inne
                in 0x0400..0x04FF -> true // Cyrylica
                in 0x0E00..0x0E7F -> true // Tajski
                in 0x0530..0x058F -> true // Ormiański
                else -> false
            }
        }
    }
}