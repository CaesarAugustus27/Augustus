package org.octavius.shared.contract

/**
 * Generyczny interfejs parsera.
 * @param T Typ danych, który ten parser produkuje (musi dziedziczyć po ParsedData).
 */
interface Parser<T : ParsedData> {
    // Identyfikator modułu, do którego należy ten parser. Niezbędny.
    val moduleId: String

    fun canParse(url: String): Boolean

    // Zwraca konkretny, silnie typowany obiekt danych lub null.
    suspend fun parse(): ParsedData?

    // Serializuje obiekt *swojego* typu do stringa JSON.
    fun serialize(data: T): String
}