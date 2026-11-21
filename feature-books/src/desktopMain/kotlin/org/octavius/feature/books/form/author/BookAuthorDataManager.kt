package org.octavius.feature.books.form.author

import io.github.octaviusframework.driver.exception.ConstraintViolationException
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial
import org.octavius.feature.books.localization.BooksTr

class BookAuthorDataManager : FormDataManager() {

    private fun loadData(loadedId: Any?) = loadData(loadedId) {
        from("books.authors", "a")
        map("id")
        map("name")
        map("sort_name")
    }

    override fun initData(
        payload: Map<String, Any?>
    ): Map<String, Any?>? {
        val loadedData = loadData(payload["id"]) ?: return null
        return loadedData + payload
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> processSave(formData) },
            "delete" to { formData -> processDelete(formData) }
        )
    }

    private fun processSave(formResultData: FormResultData): Boolean {
        val loadedId = formResultData.getInitial("id")
        val params = mapOf(
            "name" to formResultData.getCurrent("name"),
            "sort_name" to formResultData.getCurrent("sort_name")
        )

        try {
            if (loadedId != null) {
                db.update("books.authors")
                    .setValues(params)
                    .where("id = @id")
                    .executeUpdate(params + ("id" to loadedId))
            } else {
                db.insertInto("books.authors")
                    .values(params)
                    .executeUpdate(params)
            }
        } catch (e: ConstraintViolationException) {
            if (e.constraint != "authors_name_key") throw e
            errorManager.setFieldErrors("name", listOf(BooksTr.Authors.Validation.nameExists()))
            return false
        }
        return true
    }

    private fun processDelete(formResultData: FormResultData): Boolean {
        // Brak id oznacza, ze nie ma czego usuwac - nie jest to blad, wiec formularz
        // ma sie normalnie zamknac. Przycisk usuwania i tak jest ukryty gdy id == null.
        val loadedId = formResultData.getInitial("id") ?: return true

        // book_to_authors ma ON DELETE RESTRICT: autora z książkami baza nie usunie.
        try {
            db.deleteFrom("books.authors")
                .where("id = @id")
                .executeUpdate(mapOf("id" to loadedId))
        } catch (e: ConstraintViolationException) {
            if (e.constraint != "book_to_authors_author_id_fkey") throw e
            errorManager.addGlobalError(BooksTr.Authors.Validation.hasBooks())
            return false
        }
        return true
    }
}