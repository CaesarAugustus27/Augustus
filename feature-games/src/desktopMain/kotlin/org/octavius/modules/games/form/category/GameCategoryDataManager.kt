package org.octavius.modules.games.form.category

import io.github.octaviusframework.driver.exception.ConstraintViolationException
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial
import org.octavius.modules.games.localization.GamesTr

class GameCategoryDataManager : FormDataManager() {

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

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> processSave(formData) }
        )
    }

    private fun processSave(formResultData: FormResultData): Boolean {
        val loadedId = formResultData.getInitial("id")
        val params = mapOf("name" to formResultData.getCurrent("name"))
        try {
            if (loadedId != null) {
                db.update("games.categories").setValue("name").where("id = @id")
                    .executeUpdate(params + ("id" to loadedId))
            } else {
                db.insertInto("games.categories").value("name").executeUpdate(params)
            }
        } catch (e: ConstraintViolationException) {
            if (e.constraint != "categories_name_key") throw e
            errorManager.setFieldErrors("name", listOf(GamesTr.Validation.nameExists()))
            return false
        }
        return true
    }
}
