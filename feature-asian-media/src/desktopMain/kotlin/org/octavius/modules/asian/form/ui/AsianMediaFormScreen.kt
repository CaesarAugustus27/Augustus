package org.octavius.modules.asian.form.ui

import io.github.octaviusframework.form.FormHandler
import io.github.octaviusframework.form.FormView
import org.octavius.modules.asian.form.AsianMediaFormDataManager
import org.octavius.modules.asian.form.AsianMediaFormSchemaBuilder
import org.octavius.modules.asian.form.AsianMediaValidator
import org.octavius.modules.asian.localization.AsianMediaTr
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class AsianMediaFormScreen {
    companion object {
        fun create(
            entityId: Int? = null,
            payload: Map<String, Any?> = emptyMap()
        ): Screen {
            val title =
                if (entityId == null) AsianMediaTr.Form.newTitle() else AsianMediaTr.Form.editTitle()

            val formHandler = FormHandler(
                formSchemaBuilder = AsianMediaFormSchemaBuilder(),
                formDataManager = AsianMediaFormDataManager(),
                formValidator = AsianMediaValidator(),
                payload = payload + ("id" to entityId)
            )

            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
