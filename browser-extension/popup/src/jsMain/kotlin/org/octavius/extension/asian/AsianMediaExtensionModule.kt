package org.octavius.extension.asian

import kotlinx.serialization.json.Json
import org.octavius.extension.app.ExtensionModule
import org.octavius.modules.asian.model.AsianPublicationData
import org.octavius.navigation.Screen

object AsianMediaExtensionModule : ExtensionModule {
    override val id: String = AsianPublicationData.MODULE_ID

    override fun createScreenFromJson(jsonData: String): Screen? {
        return try {
            val data = Json.decodeFromString<AsianPublicationData>(jsonData)
            chooseScreen(data)
        } catch (e: Exception) {
            println("Błąd deserializacji danych dla modułu $id: ${e.message}")
            null
        }
    }

    fun chooseScreen(publicationData: AsianPublicationData): Screen {
        return AsianMediaAddScreen(publicationData)
    }

}