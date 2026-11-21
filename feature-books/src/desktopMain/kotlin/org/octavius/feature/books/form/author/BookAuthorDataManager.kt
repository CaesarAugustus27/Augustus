package org.octavius.feature.books.form.author

import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.database.ConstraintError
import io.github.octaviusframework.form.database.DatabaseFormDataManager
import org.octavius.feature.books.localization.BooksTr

class BookAuthorDataManager : DatabaseFormDataManager() {

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

    override fun definedFormPlans(): Map<String, (FormResultData) -> TransactionPlan> {
        return mapOf(
            "save" to { formData -> planSave(formData) },
            "delete" to { formData -> planDelete(formData) }
        )
    }

    // book_to_authors ma ON DELETE RESTRICT: autora z książkami baza nie usunie.
    override fun constraintErrors() = mapOf(
        "authors_name_key" to ConstraintError.Field("name", BooksTr.Authors.Validation.nameExists()),
        "book_to_authors_author_id_fkey" to ConstraintError.Global(BooksTr.Authors.Validation.hasBooks())
    )

    private fun planSave(formResultData: FormResultData): TransactionPlan {
        val loadedId = formResultData.getInitial("id")
        val params = mapOf(
            "name" to formResultData.getCurrent("name"),
            "sort_name" to formResultData.getCurrent("sort_name")
        )

        return TransactionPlan().apply {
            if (loadedId != null) {
                update("books.authors")
                    .setValues(params)
                    .where("id = @id")
                    .asStep()
                    .executeUpdate(params + ("id" to loadedId))
            } else {
                insertInto("books.authors")
                    .values(params)
                    .asStep()
                    .executeUpdate(params)
            }
        }
    }

    private fun planDelete(formResultData: FormResultData): TransactionPlan {
        // Brak id oznacza, ze nie ma czego usuwac - nie jest to blad, wiec pusty plan konczy sie sukcesem
        // i formularz normalnie sie zamyka. Przycisk usuwania i tak jest ukryty gdy id == null.
        val loadedId = formResultData.getInitial("id") ?: return TransactionPlan()

        return TransactionPlan().apply {
            deleteFrom("books.authors").where("id = @id").asStep().executeUpdate("id" to loadedId)
        }
    }
}
