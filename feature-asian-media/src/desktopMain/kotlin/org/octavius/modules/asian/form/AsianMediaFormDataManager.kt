package org.octavius.modules.asian.form

import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.client.transaction.TransactionValue
import io.github.octaviusframework.client.transaction.toTransactionValue
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.control.base.getInitialAs
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue
import io.github.octaviusframework.form.database.DatabaseFormDataManager

class AsianMediaFormDataManager : DatabaseFormDataManager() {

    private fun loadAsianMediaData(loadedId: Any?) = loadData(loadedId) {
        from("asian_media.titles", "t")

        // Proste mapowania z tabeli 'titles'
        map("id")
        map("title_info/titles")
        map("title_info/language")

        // Relacja 1-do-N z 'publications'
        mapRelatedList("publications") {
            from("asian_media.publications", "p")
            linkedBy("p.title_id")
            // Typ jest unikalny w obrębie tytułu, więc kolejność enuma w bazie wystarcza
            orderBy("p.publication_type")

            map("id")
            map("publication_type")
            map("status")
            map("track_progress")

            mapOneToOne {
                from("asian_media.publication_volumes", "pv")
                on("pv.publication_id = p.id")
                map("volumes")
                map("translated_volumes")
                map("chapters")
                map("translated_chapters")
                map("original_completed")
            }
        }
    }

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
        val loadedData = loadAsianMediaData(payload["id"]) ?: return null

        // Kolejność łączenia: Załadowane z DB -> Payload (nadpisuje wszystko)
        return loadedData + payload
    }

    override fun definedFormPlans(): Map<String, (formResultData: FormResultData) -> TransactionPlan> {
        return mapOf(
            "save" to { formData -> planSave(formData) },
            "delete" to { formData -> planDelete(formData) /* Istnienie ID zapewnia logika ukrywania przycisku */ }
        )
    }

    fun planDelete(formResultData: FormResultData): TransactionPlan {
        // Wykorzystanie CASCADE
        return TransactionPlan().apply {
            deleteFrom("asian_media.titles")
                .where("id = @id")
                .asStep()
                .executeUpdate("id" to formResultData.getInitial("id"))
        }
    }

    fun planSave(formResultData: FormResultData): TransactionPlan {
        val plan = TransactionPlan()

        with(plan) {
            // =================================================================================
            // KROK 1: Główna encja 'titles'
            // =================================================================================
            val titleIdRef: TransactionValue<Int>

            val titleData = mapOf(
                "titles" to formResultData.getCurrent("title_info/titles"),
                "language" to formResultData.getCurrent("title_info/language")
            )

            if (formResultData.getInitial("id") != null) {
                // TRYB EDYCJI: ID jest znane.
                titleIdRef = formResultData.getInitialAs<Int>("id").toTransactionValue()
                update("asian_media.titles")
                    .setValues(titleData)
                    .where("id = @id")
                    .asStep()
                    .executeUpdate(titleData + mapOf("id" to titleIdRef))
            } else {
                titleIdRef = insertInto("asian_media.titles")
                    .values(titleData)
                    .returning("id")
                    .asStep()
                    .fetchField<Int>(titleData)
                    .value()
            }

            // =================================================================================
            // KROK 2: Obsługa pod-encji 'publications'
            // =================================================================================
            val publicationsResult = formResultData.getCurrentAs<RepeatableResultValue>("publications")

            // --- TRYB EDYCJI: Rozpatrujemy zmiany ---
            if (formResultData.getInitial("id") != null) {
                // Usunięte publikacje
                publicationsResult.deletedRows.forEach { rowData ->
                    val pubId = rowData.getInitialAs<Int>("id")
                    deleteFrom("asian_media.publications")
                        .where("id = @id")
                        .asStep()
                        .executeUpdate("id" to pubId)
                }

                // Zmodyfikowane publikacje
                publicationsResult.modifiedRows.forEach { rowData ->
                    val pubId = rowData.getInitialAs<Int>("id")
                    val publicationIdRef = pubId.toTransactionValue()
                    val publicationData = mapOf(
                        "publication_type" to rowData.getCurrent("publication_type"),
                        "status" to rowData.getCurrent("status"),
                        "track_progress" to rowData.getCurrent("track_progress")
                    )

                    update("asian_media.publications")
                        .setValues(publicationData)
                        .where("id = @id")
                        .asStep()
                        .executeUpdate(publicationData + mapOf("id" to publicationIdRef))

                    // Warunkowo zaktualizuj powiązane 'publication_volumes'
                    addPublicationVolumesUpdateOperation(plan, rowData, publicationIdRef)
                }
            }

            // --- TRYB TWORZENIA lub DODAWANIA NOWYCH (wspólna logika) ---
            // W trybie tworzenia `allCurrentRows` = `addedRows`
            val rowsToAdd = if (formResultData.getInitial("id") == null) publicationsResult.allCurrentRows else publicationsResult.addedRows

            rowsToAdd.forEach { rowData ->
                val publicationData = mapOf(
                    "publication_type" to rowData.getCurrent("publication_type"),
                    "status" to rowData.getCurrent("status"),
                    "track_progress" to rowData.getCurrent("track_progress"),
                    "title_id" to titleIdRef
                )

                val newPublicationIdRef = insertInto("asian_media.publications")
                    .values(publicationData)
                    .returning("id")
                    .asStep()
                    .fetchField<Int>(publicationData)
                    .value()

                addPublicationVolumesUpdateOperation(plan, rowData, newPublicationIdRef)
            }
        }

        return plan
    }

    private fun addPublicationVolumesUpdateOperation(
        plan: TransactionPlan,
        rowData: FormResultData,
        publicationIdRef: TransactionValue<Int>
    ) {
        if (rowData.getCurrentAs<Boolean>("track_progress")) {
            val volumesData = mapOf(
                "volumes" to rowData.getCurrent("volumes"),
                "translated_volumes" to rowData.getCurrent("translated_volumes"),
                "chapters" to rowData.getCurrent("chapters"),
                "translated_chapters" to rowData.getCurrent("translated_chapters"),
                "original_completed" to rowData.getCurrent("original_completed")
            )

            with(plan) {
                update("asian_media.publication_volumes")
                    .setValues(volumesData)
                    .where("publication_id = @publication_id")
                    .asStep()
                    .executeUpdate(volumesData + mapOf("publication_id" to publicationIdRef))
            }
        }
    }
}