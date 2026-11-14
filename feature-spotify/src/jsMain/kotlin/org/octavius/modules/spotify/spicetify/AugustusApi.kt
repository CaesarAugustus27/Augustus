package org.octavius.modules.spotify.spicetify

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.js.json

/** HTTP do Augustusa (aplikacja na 127.0.0.1:8080). Odpowiedź z błędem rzuca wyjątkiem z kodem i treścią. */
object AugustusApi {
    const val BASE_URL = "http://127.0.0.1:8080/api/spotify"

    suspend inline fun <reified ResponseBody> get(path: String): ResponseBody =
        read(window.fetch("$BASE_URL$path").await())

    suspend inline fun <reified RequestBody, reified ResponseBody> post(path: String, body: RequestBody): ResponseBody {
        val init = RequestInit(
            method = "POST",
            headers = json("Content-Type" to "application/json"),
            body = Json.encodeToString(body)
        )
        return read(window.fetch("$BASE_URL$path", init).await())
    }

    suspend inline fun <reified ResponseBody> read(response: Response): ResponseBody {
        val text = response.text().await()
        if (!response.ok) throw IllegalStateException("${response.status} $text")
        return Json.decodeFromString(text)
    }
}
