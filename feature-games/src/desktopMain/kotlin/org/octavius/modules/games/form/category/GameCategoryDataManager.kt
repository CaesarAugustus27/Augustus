package org.octavius.modules.games.form.category

import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.database.ConstraintError
import io.github.octaviusframework.form.database.DatabaseFormDataManager
import org.octavius.modules.games.localization.GamesTr

class GameCategoryDataManager : DatabaseFormDataManager() {

    private fun loadData(loadedId: Any?) = loadData(loadedId) {
        from("games.categories", "ctg")
        map("id")
        map("name")
    }

    override fun initData(
        payload: Map<String, Any?>
    ): Map<String, Any?>? {
        val loadedData = loadData(payload["id"]) ?: return null
        return loadedData + payload
    }

    override fun definedFormPlans(): Map<String, (FormResultData) -> TransactionPlan> {
        return mapOf(
            "save" to { formData -> planSave(formData) }
        )
    }

    override fun constraintErrors() = mapOf(
        "categories_name_key" to ConstraintError.Field("name", GamesTr.Validation.nameExists())
    )

    private fun planSave(formResultData: FormResultData): TransactionPlan {
        val loadedId = formResultData.getInitial("id")
        val params = mapOf("name" to formResultData.getCurrent("name"))
        return TransactionPlan().apply {
            if (loadedId != null) {
                update("games.categories").setValue("name").where("id = @id").asStep()
                    .executeUpdate(params + ("id" to loadedId))
            } else {
                insertInto("games.categories").value("name").asStep().executeUpdate(params)
            }
        }
    }
}
