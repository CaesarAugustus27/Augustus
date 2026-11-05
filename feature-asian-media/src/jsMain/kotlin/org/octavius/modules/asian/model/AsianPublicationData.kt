package org.octavius.modules.asian.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.octavius.shared.contract.ParsedData
import org.octavius.domain.asian.PublicationLanguage
import org.octavius.domain.asian.PublicationType

@Serializable
@SerialName("AsianPublicationData")
data class AsianPublicationData(
    override val source: String,
    val titles: List<String>,
    val type: PublicationType,
    val language: PublicationLanguage,
    val externalId: ExternalId? = null
) : ParsedData {
    companion object {
        /** Moduł popupu, który przyjmuje te dane. */
        const val MODULE_ID = "asian-media"
    }
}
