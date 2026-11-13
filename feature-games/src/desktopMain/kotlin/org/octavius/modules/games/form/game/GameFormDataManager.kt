package org.octavius.modules.games.form.game

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.dbResult
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.client.transaction.TransactionValue
import io.github.octaviusframework.client.transaction.toTransactionValue
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import org.octavius.domain.game.GameStatus
import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrent
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.base.getInitial
import io.github.octaviusframework.form.control.base.getInitialAs
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue

class GameFormDataManager : FormDataManager() {

    private fun loadGameData(loadedId: Any?) = loadData(loadedId) {
        from("games", "g")

        // Proste mapowania z tabeli 'games'
        map("id")
        map("basic_info/name")
        map("basic_info/series")
        map("basic_info/status")

        // Relacja 1-do-1 z 'play_time'
        mapOneToOne {
            from("play_time", "pt")
            on("g.id = pt.game_id")
            existenceFlag("play_time_exists", "pt.game_id")
            map("play_time_section/play_time_hours")
            map("play_time_section/completion_count")
        }

        // Relacja 1-do-1 z 'ratings'
        mapOneToOne {
            from("ratings", "r")
            on("g.id = r.game_id")
            existenceFlag("ratings_exists", "r.game_id")
            map("ratings_section/story_rating")
            map("ratings_section/gameplay_rating")
            map("ratings_section/atmosphere_rating")
        }

        // Relacja 1-do-1 z 'characters'
        mapOneToOne {
            from("characters", "c")
            on("g.id = c.game_id")
            existenceFlag("characters_exists", "c.game_id")
            map("characters_section/has_distinctive_character")
            map("characters_section/has_distinctive_protagonist")
            map("characters_section/has_distinctive_antagonist")
        }

        // Relacja 1-do-N z 'categories'
        mapRelatedList("categories") {
            from("categories_to_games", "ctg")
            linkedBy("ctg.game_id")
            map("category", "category_id")
        }
    }

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
        val loadedId = payload["id"]
        val loadedData = loadGameData(loadedId) ?: return null

        val defaultData = if (loadedId == null) {
            mapOf(
                "basic_info/visible_characters_section" to false,
                "play_time_exists" to false,
                "ratings_exists" to false,
                "characters_exists" to false,
                "categories" to emptyList<Map<String, Any?>>()
            )
        } else {
            mapOf("basic_info/visible_characters_section" to (loadedData["characters_exists"] as Boolean))
        }

        // Kolejność łączenia: Domyślne -> Załadowane z DB -> Payload (nadpisuje wszystko)
        return defaultData + loadedData + payload
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> {
        return mapOf(
            "save" to { formData -> processSave(formData) },
            "delete" to { formData -> processDelete(formData) /* Istnienie ID zapewnia logika ukrywania przycisku */ }
        )
    }

    fun processDelete(formResultData: FormResultData): Boolean {
        // Wykorzystanie CASCADE
        val plan = TransactionPlan().apply {
            deleteFrom("games.games")
                .where("id = @id")
                .asStep()
                .executeUpdate("id" to formResultData.getInitial("id"))
        }

        return when (val result = dbResult { db.executeTransactionPlan(plan) }) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }
            is DataResult.Success -> true
        }
    }

    fun processSave(formResultData: FormResultData): Boolean {
        val plan = TransactionPlan()
        val statusesWithDetails = listOf(GameStatus.WithoutTheEnd, GameStatus.Playing, GameStatus.Played)
        val loadedId = formResultData.getInitialAs<Int?>("id")

        with(plan) {
            // =================================================================================
            // KROK 1: Główna encja 'games'
            // =================================================================================
            val gameIdRef: TransactionValue<Int>

            val gameData = mapOf(
                "name" to formResultData.getCurrent("basic_info/name"),
                "series" to formResultData.getCurrent("basic_info/series"),
                "status" to formResultData.getCurrent("basic_info/status")
            )

            // W zależności od tego, czy tworzymy nową grę, czy edytujemy istniejącą,
            // nasza referencja do ID będzie albo stałą wartością, albo odwołaniem do wyniku przyszłej operacji.
            if (loadedId != null) {
                // === TRYB EDYCJI ===
                // ID gry jest znane, więc używamy stałej wartości.
                gameIdRef = loadedId.toTransactionValue()
                // Zaktualizuj grę, przekazując wszystkie parametry
                update("games.games")
                    .setValues(gameData)
                    .where("id = @id")
                    .asStep()
                    .executeUpdate(gameData + mapOf("id" to gameIdRef))
            } else {
                // Wstaw nową grę i pobierz jej ID jako referencję
                gameIdRef = insertInto("games.games")
                    .values(gameData)
                    .returning("id")
                    .asStep()
                    .fetchField<Int>(gameData) // Używamy fetchField, bo chcemy pojedynczą wartość
                    .value()
            }

            val status = formResultData.getCurrentAs<GameStatus>("basic_info/status")

            // =================================================================================
            // KROK 2: Obsługa tabel zależnych 1-do-1
            // =================================================================================

            // --- Obsługa Play Time ---
            handleDependentTable(
                plan = plan,
                exists = formResultData.getCurrentAs("play_time_exists"),
                conditionMet = status in statusesWithDetails,
                tableName = "games.play_time",
                data = mapOf(
                    "play_time_hours" to formResultData.getCurrent("play_time_section/play_time_hours"),
                    "completion_count" to formResultData.getCurrent("play_time_section/completion_count")
                ),
                gameIdRef = gameIdRef
            )

            // --- Obsługa Ratings ---
            handleDependentTable(
                plan = plan,
                exists = formResultData.getCurrentAs("ratings_exists"),
                conditionMet = status in statusesWithDetails,
                tableName = "games.ratings",
                data = mapOf(
                    "story_rating" to formResultData.getCurrent("ratings_section/story_rating"),
                    "gameplay_rating" to formResultData.getCurrent("ratings_section/gameplay_rating"),
                    "atmosphere_rating" to formResultData.getCurrent("ratings_section/atmosphere_rating")
                ),
                gameIdRef = gameIdRef
            )

            // --- Obsługa Characters ---
            handleDependentTable(
                plan = plan,
                exists = formResultData.getCurrentAs("characters_exists"),
                conditionMet = formResultData.getCurrentAs("basic_info/visible_characters_section"),
                tableName = "games.characters",
                data = mapOf(
                    "has_distinctive_character" to formResultData.getCurrent("characters_section/has_distinctive_character"),
                    "has_distinctive_protagonist" to formResultData.getCurrent("characters_section/has_distinctive_protagonist"),
                    "has_distinctive_antagonist" to formResultData.getCurrent("characters_section/has_distinctive_antagonist")
                ),
                gameIdRef = gameIdRef
            )

            // =================================================================================
            // KROK 3: Obsługa tabeli łączącej many-to-many
            // =================================================================================

            val categoriesResult = formResultData.getCurrentAs<RepeatableResultValue>("categories")
            // Usunięte kategorie
            val deletedCategories = categoriesResult.deletedRows.map { rowData ->
                rowData.getInitialAs<Int>("category")
            } + categoriesResult.modifiedRows.map { rowData ->
                rowData.getInitialAs<Int>("category")
            }

            deleteFrom("games.categories_to_games ctg")
                .using("UNNEST(@ids_to_delete) AS t(id)")
                .where("ctg.category_id = t.id AND ctg.game_id = @game_id")
                .asStep().executeUpdate("ids_to_delete" to deletedCategories.withPgType(PgStandardType.INT4_ARRAY), "game_id" to gameIdRef)

            val insertedCategories = categoriesResult.addedRows.map { rowData ->
                rowData.getCurrentAs<Int>("category")
            } + categoriesResult.modifiedRows.map { rowData ->
                rowData.getCurrentAs<Int>("category")
            }

            insertInto("games.categories_to_games")
                .fromSelect(select("category_id", "@game_id").from("UNNEST(@ids_to_insert) AS category_id").toSql())
                .asStep().executeUpdate("ids_to_insert" to insertedCategories.withPgType(PgStandardType.INT4_ARRAY), "game_id" to gameIdRef)
        }

        // Wykonanie całego planu
        return when (val result = dbResult { db.executeTransactionPlan(plan) }) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }

            is DataResult.Success -> true
        }
    }

    /**
     * Metoda pomocnicza do obsługi logiki INSERT/UPDATE/DELETE dla tabel zależnych 1-do-1.
     * Hermetyzuje powtarzalny wzorzec.
     */
    private fun handleDependentTable(
        plan: TransactionPlan,
        exists: Boolean,
        conditionMet: Boolean,
        tableName: String,
        data: Map<String, Any?>,
        gameIdRef: TransactionValue<Int>
    ) {
        with(plan) {
            if (exists) {
                if (conditionMet) { // UPDATE
                    val allParams = data + mapOf("game_id" to gameIdRef)
                    update(tableName)
                        .setValues(data)
                        .where("game_id = @game_id")
                        .asStep()
                        .executeUpdate(allParams)
                } else { // DELETE
                    deleteFrom(tableName)
                        .where("game_id = @game_id")
                        .asStep()
                        .executeUpdate("game_id" to gameIdRef)
                }
            } else if (conditionMet) { // INSERT
                val allParams = data + mapOf("game_id" to gameIdRef)
                insertInto(tableName)
                    .values(allParams)
                    .asStep()
                    .executeUpdate(allParams)
            }
        }
    }
}