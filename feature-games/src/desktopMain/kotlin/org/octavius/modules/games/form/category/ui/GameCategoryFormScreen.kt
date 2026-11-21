package org.octavius.modules.games.form.category.ui

import io.github.octaviusframework.form.component.FormHandler
import io.github.octaviusframework.form.component.FormView
import org.octavius.modules.games.form.category.GameCategoryDataManager
import org.octavius.modules.games.form.category.GameCategorySchemaBuilder
import org.octavius.modules.games.localization.GamesTr
import org.octavius.navigation.ComponentScreen
import org.octavius.navigation.Screen

class GameCategoryFormScreen {

    companion object {
        fun create(
            entityId: Int? = null
        ): Screen {
            val title =
                if (entityId == null) GamesTr.Form.newCategory() else GamesTr.Form.editCategory()

            val formHandler = FormHandler(
                formSchemaBuilder = GameCategorySchemaBuilder(),
                formDataManager = GameCategoryDataManager(),
                payload = mapOf("id" to entityId)
            )
            return ComponentScreen(title) { FormView(formHandler) }
        }
    }
}
