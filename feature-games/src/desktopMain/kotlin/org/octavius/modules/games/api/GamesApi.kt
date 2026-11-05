package org.octavius.modules.games.api

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.OctaviusClient
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.octavius.contract.ApiModule
import org.octavius.domain.game.GameStatus
import org.octavius.modules.games.model.SteamIgnore
import org.octavius.modules.games.model.SteamIgnoreResponse

/**
 * Endpointy modułu gier dla wtyczki przeglądarki.
 */
class GamesApi : ApiModule, KoinComponent {
    private val db: OctaviusClient by inject()

    override fun installRoutes(routing: Routing) {
        routing.route("/api/games") {
            // Gry zignorowane na SteamDB, podsłuchane przez wtyczkę
            mirrorSteamIgnore()
        }
    }

    /**
     * Definiuje endpoint: POST /api/games/steam/ignore
     * Powtarza w bazie zignorowanie gry na Steamie: grę znajdujemy po appid, a bez appid - po nazwie.
     * Nie przełącza aplikacji na formularz, bo ignorowań przychodzi po kilkanaście naraz.
     */
    private fun Route.mirrorSteamIgnore() {
        post("/steam/ignore") {
            val ignore = call.receive<SteamIgnore>()
            // Steam dokleja do nazw ™, ® i emoji, a w bazie prawie ich nie ma, więc nowa gra dostaje nazwę bez
            // nich. Emoji bywają sekwencjami, stąd też selektor wariantu i łącznik.
            val name = ignore.name.replace(Regex("[\\p{So}\\uFE0F\\u200D]"), "").replace(Regex("\\s+"), " ").trim()
            val params = mapOf(
                "appId" to ignore.appId.toString(),
                "name" to name
            )

            val response = when (val result = db.rawQuery(STEAM_IGNORE_SQL).asResult().fetchRowStrict(params)) {
                is DataResult.Failure -> SteamIgnoreResponse(false, "Błąd bazy: ${result.error.message}")
                is DataResult.Success -> {
                    val row = result.value
                    val outcome: String = row["result"]
                    val gameName: String = row["name"]
                    val previous: GameStatus? = row["previous_status"]
                    val trash = GameStatus.Trash.toDisplayString()
                    when (outcome) {
                        "CREATED" -> SteamIgnoreResponse(true, "Dodano „$gameName”: $trash")
                        "AMBIGUOUS" -> {
                            val candidates: List<String> = row["candidates"]
                            SteamIgnoreResponse(false, "Nie zapisano, „$gameName” pasuje do: ${candidates.joinToString(", ") { "„$it”" }}")
                        }
                        // KNOWN albo LINKED, więc gra była już w bazie
                        else -> {
                            val linked = if (outcome == "LINKED") "Podpięto appid, " else ""
                            val change = if (previous == GameStatus.Trash) "już $trash" else "${previous?.toDisplayString()} → $trash"
                            SteamIgnoreResponse(true, "$linked„$gameName”: $change")
                        }
                    }
                }
            }
            call.respond(response)
        }
    }
}

/**
 * Całe zignorowanie w jednej instrukcji, więc bez osobnej transakcji. Kolejność decyzji jak przy
 * przeniesieniach na NovelUpdates:
 *
 * - appid już jest w bazie: tylko zmiana statusu,
 * - bez appid dokładnie jedna gra o tej nazwie: podpięcie appid i zmiana statusu. Nazwy porównujemy po
 *   samych literach i cyfrach, bez wielkości liter, bo Steam i baza różnią się interpunkcją, znakami
 *   ™ ® ©, emoji i apostrofami („Watch_Dogs® 2” to „Watch Dogs 2”). Nazwa gry jest unikalna, więc ta
 *   sama nazwa to ta sama gra, także gdy ma już inny appid (np. wydanie pod nowym appid),
 * - nic nie pasuje: nowa gra z appid,
 * - kilka gier o tej nazwie (duplikaty różniące się interpunkcją) jest niejednoznaczne i nic nie zapisuje.
 *
 * TRASH nie ma czasu gry ani ocen, więc znikają one tak samo jak przy zmianie statusu w formularzu -
 * inaczej taka gra liczyłaby się w statystykach najczęściej granych i najwyżej ocenianych.
 */
private const val STEAM_IGNORE_SQL = """
WITH known AS (
    SELECT game_id
    FROM games.game_external_ids
    WHERE site = 'STEAM'
      AND external_id = @appId
),
     candidates AS (
         SELECT g.id, g.name
         FROM games.games g
         WHERE lower(regexp_replace(g.name, '[^[:alnum:]]+', '', 'g')) = lower(regexp_replace(@name, '[^[:alnum:]]+', '', 'g'))
           AND NOT EXISTS (SELECT 1 FROM known)
     ),
     decision AS (
         SELECT CASE
                    WHEN EXISTS (SELECT 1 FROM known) THEN 'KNOWN'
                    WHEN NOT EXISTS (SELECT 1 FROM candidates) THEN 'CREATED'
                    WHEN (SELECT count(*) FROM candidates) = 1 THEN 'LINKED'
                    ELSE 'AMBIGUOUS'
                    END AS result
     ),
     existing AS (
         SELECT g.id, g.name, g.status
         FROM games.games g
         WHERE g.id IN (SELECT game_id FROM known)
            OR (g.id IN (SELECT id FROM candidates) AND (SELECT result FROM decision) = 'LINKED')
     ),
     new_game AS (
         INSERT INTO games.games (name, status)
             SELECT @name, 'TRASH'::games.game_status
             FROM decision
             WHERE result = 'CREATED'
             RETURNING id
     ),
     new_app AS (
         INSERT INTO games.game_external_ids (game_id, site, external_id)
             SELECT id, 'STEAM'::games.source_site, @appId
             FROM new_game
             UNION ALL
             SELECT id, 'STEAM', @appId
             FROM existing
             WHERE (SELECT result FROM decision) = 'LINKED'
     ),
     updated_status AS (
         UPDATE games.games
             SET status = 'TRASH'
             WHERE id IN (SELECT id FROM existing)
               AND status <> 'TRASH'
     ),
     deleted_play_time AS (
         DELETE FROM games.play_time
             WHERE game_id IN (SELECT id FROM existing)
     ),
     deleted_ratings AS (
         DELETE FROM games.ratings
             WHERE game_id IN (SELECT id FROM existing)
     )
SELECT d.result,
       coalesce(e.name, @name)                                AS name,
       e.status                                               AS previous_status,
       (SELECT array_agg(name ORDER BY name) FROM candidates) AS candidates
FROM decision d
         LEFT JOIN existing e ON true
"""
