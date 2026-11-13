package org.octavius.feature.books.form.book

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.dbResult
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.client.transaction.TransactionValue
import io.github.octaviusframework.client.transaction.toTransactionValue
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.control.base.getInitialAs
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue

class BookFormDataManager : FormDataManager() {

    private fun loadBookData(loadedId: Any?) = loadData(loadedId) {
        from("books.books", "b")

        map("id")
        map("basic_info/title_pl")
        map("basic_info/title_eng")
        map("basic_info/status")

        // Relacja N-do-M z autorami
        mapRelatedList("authors") {
            from("books.book_to_authors", "bta")
            linkedBy("bta.book_id")
            map("author_id", "author_id")
        }
    }

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
        val loadedId = payload["id"]
        val loadedData = loadBookData(loadedId) ?: return null

        val defaultData = if (loadedId == null) {
            mapOf(
                "authors" to emptyList<Map<String, Any?>>()
            )
        } else {
            emptyMap()
        }

        return defaultData + loadedData + payload
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> processSave(formData) },
            "delete" to { formData -> processDelete(formData) }
        )
    }

    private fun processSave(formResultData: FormResultData): Boolean {
        val loadedId = formResultData.getInitial("id")

        val plan = TransactionPlan().apply {
            // =================================================================================
            // KROK 1: Główna encja 'books'
            // =================================================================================
            val bookIdRef: TransactionValue<Int>

            val bookData = mapOf(
                "title_pl" to formResultData.getCurrent("basic_info/title_pl"),
                "title_eng" to formResultData.getCurrent("basic_info/title_eng"),
                "status" to formResultData.getCurrent("basic_info/status")
            )

            if (loadedId != null) {
                bookIdRef = (loadedId as Int).toTransactionValue()
                update("books.books")
                    .setValues(bookData)
                    .setExpression("updated_at", "NOW()")
                    .where("id = @id")
                    .asStep()
                    .executeUpdate(bookData + mapOf("id" to bookIdRef))
            } else {
                bookIdRef = insertInto("books.books")
                    .values(bookData)
                    .returning("id")
                    .asStep()
                    .fetchField<Int>(bookData)
                    .value()
            }

            // =================================================================================
            // KROK 2: Obsługa tabeli łączącej many-to-many (autorzy)
            // =================================================================================
            val authorsResult = formResultData.getCurrentAs<RepeatableResultValue>("authors")

            // Usunięci i zmodyfikowani autorzy (stare wartości do usunięcia)
            val deletedAuthors = authorsResult.deletedRows.map { rowData ->
                rowData.getInitialAs<Int>("author_id")
            } + authorsResult.modifiedRows.map { rowData ->
                rowData.getInitialAs<Int>("author_id")
            }

            if (deletedAuthors.isNotEmpty()) {
                deleteFrom("books.book_to_authors bta")
                    .using("UNNEST(@ids_to_delete) AS t(id)")
                    .where("bta.author_id = t.id AND bta.book_id = @book_id")
                    .asStep()
                    .executeUpdate(
                        "ids_to_delete" to deletedAuthors.withPgType(PgStandardType.INT4_ARRAY),
                        "book_id" to bookIdRef
                    )
            }

            // Dodani i zmodyfikowani autorzy (nowe wartości do wstawienia)
            val insertedAuthors = authorsResult.addedRows.map { rowData ->
                rowData.getCurrentAs<Int>("author_id")
            } + authorsResult.modifiedRows.map { rowData ->
                rowData.getCurrentAs<Int>("author_id")
            }

            if (insertedAuthors.isNotEmpty()) {
                insertInto("books.book_to_authors")
                    .fromSelect(
                        select("@book_id", "author_id")
                            .from("UNNEST(@ids_to_insert) AS author_id")
                            .toSql()
                    )
                    .asStep()
                    .executeUpdate(
                        "ids_to_insert" to insertedAuthors.withPgType(PgStandardType.INT4_ARRAY),
                        "book_id" to bookIdRef
                    )
            }
        }

        return when (val result = dbResult { db.executeTransactionPlan(plan) }) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }
            is DataResult.Success -> true
        }
    }

    private fun processDelete(formResultData: FormResultData): Boolean {
        // Brak id oznacza, ze nie ma czego usuwac - nie jest to blad, wiec formularz
        // ma sie normalnie zamknac. Przycisk usuwania i tak jest ukryty gdy id == null.
        val loadedId = formResultData.getInitial("id") ?: return true

        // CASCADE usunie powiązania w book_to_authors
        val result = db.deleteFrom("books.books")
            .where("id = @id")
            .asResult().executeUpdate(mapOf("id" to loadedId))

        return when (result) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }
            is DataResult.Success<*> -> true
        }
    }
}
