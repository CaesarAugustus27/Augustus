package org.octavius.modules.spotify.spicetify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** LocalStorage klienta Spotify z wartościami w JSON-ie. */
object ClientStorage {
    /** Wartość spod [key]; `null`, gdy jej nie ma albo to nie JSON. */
    fun read(key: String): JsonElement? = try {
        Spicetify.LocalStorage.get(key)?.let(Json::parseToJsonElement)?.takeUnless { it is JsonNull }
    } catch (_: Throwable) {
        null
    }

    fun write(key: String, value: JsonElement) = Spicetify.LocalStorage.set(key, value.toString())
}
