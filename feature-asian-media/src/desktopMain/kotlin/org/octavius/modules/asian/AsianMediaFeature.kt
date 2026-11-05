package org.octavius.modules.asian

import org.octavius.contract.ApiModule
import org.octavius.contract.FeatureModule
import org.octavius.contract.ScreenFactory
import org.octavius.modules.asian.api.AsianMediaApi
import org.octavius.modules.asian.form.ui.AsianMediaFormScreen
import org.octavius.modules.asian.navigation.AsianMediaTab
import org.octavius.navigation.Screen
import org.octavius.navigation.Tab

object AsianMediaFeature : FeatureModule {

    const val ASIAN_MEDIA_FORM_SCREEN_ID = "asianMediaForm"

    override val name: String = "asian-media"
    override val order: Int = 10
    override val schema: String = "asian_media"
    override fun getTab(): Tab = AsianMediaTab()
    override fun getApiModules(): List<ApiModule> = listOf(AsianMediaApi())
    override fun getScreenFactories(): List<ScreenFactory> = listOf(
        object : ScreenFactory {
            override val screenId: String = ASIAN_MEDIA_FORM_SCREEN_ID

            override fun create(payload: Map<String, Any>?): Screen {
                // Wyciągamy ID z payloadu
                val entityId = payload?.get("entityId") as? Int
                require(entityId != null) { "Payload musi zawierać 'entityId' dla tego ekranu." }

                return AsianMediaFormScreen.create(
                    entityId = entityId
                )
            }
        }
    )
}
