package org.octavius.shared.contract

import kotlinx.serialization.Serializable

/**
 * Kontener na wynik parsowania, który zawiera zarówno sparsowane dane,
 * jak i identyfikator modułu, który je wygenerował.
 *
 * @param moduleId Identyfikator modułu (np. "asian_media_module").
 * @param dataJson String zawierający JSON ze sparsowanymi danymi (np. AsianPublicationData).
 */
@Serializable
data class ParseResult(
    val moduleId: String,
    val dataJson: String
)