package org.octavius.modules.games.form.series

import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.form.FormResultData
import io.github.octaviusframework.form.database.ConstraintError
import io.github.octaviusframework.form.database.DatabaseFormDataManager
import io.github.octaviusframework.form.getCurrent
import io.github.octaviusframework.form.getInitial
import org.octavius.modules.games.localization.GamesTr

class GameSeriesFormDataManager : DatabaseFormDataManager() {
    private fun loadData(loadedId: Any?) = loadData(loadedId) {
        from("games.series", "s")
        map("id")
        map("basic_info/name")
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
        "series_name_key" to ConstraintError.Field("basic_info/name", GamesTr.Validation.duplicatedSeries())
    )

    private fun planSave(formResultData: FormResultData): TransactionPlan {
        val loadedId = formResultData.getInitial("id")
        val name = formResultData.getCurrent("basic_info/name")
        return TransactionPlan().apply {
            if (loadedId != null) {
                update("games.series").setValues(listOf("name")).where("id = @id").asStep()
                    .executeUpdate("name" to name, "id" to loadedId)
            } else {
                insertInto("games.series").values(listOf("name")).asStep().executeUpdate("name" to name)
            }
        }
    }
}
