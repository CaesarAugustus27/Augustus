package org.octavius.modules.games.form.series

import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.driver.exception.ConstraintViolationException
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial
import org.octavius.modules.games.localization.GamesTr

class GameSeriesFormDataManager : FormDataManager() {
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

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> processSave(formData) }
        )
    }

    private fun processSave(formResultData: FormResultData): Boolean {
        val loadedId = formResultData.getInitial("id")
        val name = formResultData.getCurrent("basic_info/name")
        val plan = TransactionPlan().apply {
            if (loadedId != null) {
                update("games.series").setValues(listOf("name")).where("id = @id").asStep()
                    .executeUpdate("name" to name, "id" to loadedId)
            } else {
                insertInto("games.series").values(listOf("name")).asStep().executeUpdate("name" to name)
            }
        }
        try {
            db.executeTransactionPlan(plan)
        } catch (e: ConstraintViolationException) {
            if (e.constraint != "series_name_key") throw e
            errorManager.setFieldErrors("basic_info/name", listOf(GamesTr.Validation.duplicatedSeries()))
            return false
        }
        return true
    }
}