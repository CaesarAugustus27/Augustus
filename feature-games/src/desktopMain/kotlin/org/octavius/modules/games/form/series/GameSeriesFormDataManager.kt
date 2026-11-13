package org.octavius.modules.games.form.series

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.dbResult
import io.github.octaviusframework.client.transaction.TransactionPlan
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial

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
        return when (val result = dbResult { db.executeTransactionPlan(plan) }) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }

            is DataResult.Success<*> -> true
        }
    }
}