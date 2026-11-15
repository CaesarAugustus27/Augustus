package org.octavius.modules.games.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private external fun encodeURIComponent(value: String): String

/**
 * Krótkie opisy i tagi gier ze sklepu Steama. Oba endpointy działają bez klucza API. `GetItems` przyjmuje
 * wiele appid naraz, a grze bez polskiego opisu sam oddaje angielski.
 */
object SteamStore {
    const val API_URL = "https://api.steampowered.com/"

    /** Tyle appid mieści się w jednym zapytaniu `GetItems` (setka daje adres na ~3 tys. znaków). */
    const val MAX_ITEMS_PER_REQUEST = 100

    private const val LANGUAGE = "polish"
    private const val TAG_COUNT = 5
    private val json = Json { ignoreUnknownKeys = true }

    /** Adres zapytania o opisy i [TAG_COUNT] najmocniejszych tagów gier [appIds]. */
    fun itemsUrl(appIds: List<Int>): String {
        val input = buildJsonObject {
            putJsonArray("ids") { appIds.forEach { addJsonObject { put("appid", it) } } }
            putJsonObject("context") {
                put("language", LANGUAGE)
                put("country_code", "PL")
            }
            putJsonObject("data_request") {
                put("include_basic_info", true)
                put("include_tag_count", TAG_COUNT)
            }
        }
        return API_URL + "IStoreBrowseService/GetItems/v1/?input_json=" + encodeURIComponent(input.toString())
    }

    /** Adres listy nazw wszystkich tagów sklepu. */
    fun tagListUrl(): String = API_URL + "IStoreService/GetTagList/v1/?language=$LANGUAGE"

    /** Gry z odpowiedzi na [itemsUrl]. Gry, której sklep nie zna (np. zdjętej ze sprzedaży), w wyniku nie ma. */
    fun parseItems(body: String): List<SteamStoreItem> =
        json.decodeFromString<ItemsEnvelope>(body).response.storeItems.mapNotNull { item ->
            val appId = item.appid ?: return@mapNotNull null
            if (item.success != 1) return@mapNotNull null
            SteamStoreItem(appId, item.basicInfo?.shortDescription?.ifBlank { null }, item.tags.map { it.tagid })
        }

    /** Nazwy tagów po numerze z odpowiedzi na [tagListUrl]. */
    fun parseTagNames(body: String): Map<Int, String> =
        json.decodeFromString<TagListEnvelope>(body).response.tags.associate { it.tagid to it.name }
}

/** Gra [appId] w sklepie Steama. [tagIds] idą od najmocniejszego, nazwy daje [SteamStore.parseTagNames]. */
class SteamStoreItem(val appId: Int, val description: String?, val tagIds: List<Int>)

@Serializable
private class ItemsEnvelope(val response: ItemsResponse)

@Serializable
private class ItemsResponse(@SerialName("store_items") val storeItems: List<StoreItem> = emptyList())

@Serializable
private class StoreItem(
    val appid: Int? = null,
    val success: Int = 0,
    @SerialName("basic_info") val basicInfo: BasicInfo? = null,
    val tags: List<TagWeight> = emptyList()
)

@Serializable
private class BasicInfo(@SerialName("short_description") val shortDescription: String? = null)

@Serializable
private class TagWeight(val tagid: Int)

@Serializable
private class TagListEnvelope(val response: TagListResponse)

@Serializable
private class TagListResponse(val tags: List<TagName> = emptyList())

@Serializable
private class TagName(val tagid: Int, val name: String)
