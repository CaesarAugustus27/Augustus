package org.octavius.modules.asian.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.octavius.contract.ApiModule
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.dbResult
import io.github.octaviusframework.client.getOrElse
import io.github.octaviusframework.client.getOrNull
import io.github.octaviusframework.client.map
import io.github.octaviusframework.client.onFailure
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import org.octavius.domain.asian.PublicationLanguage
import org.octavius.domain.asian.PublicationStatus
import org.octavius.domain.asian.PublicationType
import org.octavius.modules.asian.AsianMediaFeature
import org.octavius.modules.asian.model.ExternalId
import org.octavius.modules.asian.model.NovelUpdatesListMove
import org.octavius.modules.asian.model.NovelUpdatesListMoveResponse
import org.octavius.modules.asian.model.PublicationAddRequest
import org.octavius.modules.asian.model.PublicationAddResponse
import org.octavius.modules.asian.model.PublicationCheckRequest
import org.octavius.modules.asian.model.PublicationCheckResponse
import org.octavius.modules.asian.model.PublicationLinkRequest
import org.octavius.modules.asian.model.PublicationLinkResponse
import org.octavius.modules.asian.model.PublicationSummary
import org.octavius.modules.asian.model.TitleOpenRequest
import org.octavius.navigation.NavigationEvent
import org.octavius.navigation.NavigationEventBus

/**
 * Implementacja ApiModule dla funkcjonalności "Asian Media".
 * Definiuje endpointy do sprawdzania i dodawania publikacji.
 * Używa Koin do wstrzykiwania zależności (OctaviusClient, BatchExecutor).
 */
class AsianMediaApi : ApiModule, KoinComponent {
    private val db: OctaviusClient by inject()

    override fun installRoutes(routing: Routing) {
        routing.route("/api/asian-media") {
            // Endpoint do sprawdzania istnienia tytułu
            checkPublicationExistence()

            // Endpoint do dodawania nowego tytułu
            addNewPublication()

            // Przeniesienia serii między listami na NovelUpdates, podsłuchane przez wtyczkę
            mirrorNovelUpdatesListMove()

            // Popup: podpięcie serii do istniejącego tytułu, otwarcie w aplikacji
            linkPublication()
            openTitle()
        }
    }

    /**
     * Definiuje endpoint: GET /api/asian-media/check
     * Sprawdza, czy którykolwiek z podanych tytułów istnieje już w bazie (lub jest bardzo podobny).
     */
    private fun Route.checkPublicationExistence() {
        post("/check") {
            val request = call.receive<PublicationCheckRequest>()

            // Po identyfikatorze serii nie ma wątpliwości, więc podobieństwo tytułów sprawdzamy dopiero,
            // gdy tego identyfikatora w bazie nie ma.
            val known = request.externalId?.let(::titleByExternalId)
            if (known != null) {
                val (titleId, mainTitle) = known
                call.respond(
                    PublicationCheckResponse(
                        found = true,
                        titleId = titleId,
                        matchedTitle = mainTitle,
                        byExternalId = true,
                        publications = publicationsOf(titleId)
                    )
                )
                return@post
            }

            if (request.titles.isEmpty()) {
                call.respond(PublicationCheckResponse(found = false))
                return@post
            }

            val result = db.select("v.id", "v.matched_title")
                .from("""
                    unnest(@titles) it,
                    LATERAL (
                        SELECT title_id as id, title as matched_title
                        FROM asian_media.title_variants
                        WHERE title % it
                        ORDER BY title <-> it ASC
                        LIMIT 1
                    ) v
                """.trimIndent())
                .limit(1)
                .asResult().fetchObject<Map<String, Any?>>("titles" to request.titles.withPgType(PgStandardType.TEXT_ARRAY))

            when (result) {
                is DataResult.Failure -> {
                    println("Błąd wyszukiwania trigramowego: ${result.error.message}")
                    call.respond(PublicationCheckResponse(found = false))
                }
                is DataResult.Success -> {
                    val row = result.value
                    if (row != null) {
                        val titleId = row["id"] as Int
                        val matchedTitle = row["matched_title"] as String

                        call.respond(
                            PublicationCheckResponse(
                                found = true,
                                titleId = titleId,
                                matchedTitle = matchedTitle,
                                publications = publicationsOf(titleId)
                            )
                        )
                    } else {
                        call.respond(PublicationCheckResponse(found = false))
                    }
                }
            }
        }
    }

    /**
     * Definiuje endpoint: POST /api/asian-media/add
     * Dodaje nowy tytuł i powiązaną z nim publikację do bazy danych.
     */
    private fun Route.addNewPublication() {
        post("/add") {

            val request = call.receive<PublicationAddRequest>()

            val plan = TransactionPlan()

            // Tytuły przychodzą przefiltrowane przez parser we wtyczce, ale ten filtr jest pisany
            // na zakresach znaków i przepuszcza pisma, których nikt nie wypisał. Tutaj stoi filtr na
            // skrypcie Unicode, więc ostatnie słowo ma aplikacja. Gdyby wyciął wszystko, bierzemy to,
            // co przyszło: kolumna jest NOT NULL, a pusta lista jest gorsza niż lista do poprawienia.
            val titles = request.titles.filter { it.isNotBlank() && it.isLatinScript() }
                .ifEmpty { request.titles }

            // Krok 1: Wstaw tytuł i uzyskaj bezpieczny uchwyt do jego przyszłego ID
            val titleData = mapOf(
                "titles" to titles.withPgType(PgStandardType.TEXT_ARRAY),
                "language" to request.language
            )
            val titleIdHandle = plan.add(
                db.insertInto("asian_media.titles")
                    .values(titleData)
                    .returning("id")
                    .asStep()
                    .fetchField<Int>(titleData)
            )

            // Krok 2: Wstaw publikację, używając referencji do ID z kroku 1
            val publicationData = mapOf(
                "publication_type" to request.type,
                "status" to PublicationStatus.Trash,
                "track_progress" to false,
                "title_id" to titleIdHandle.value()
            )
            plan.add(
                db.insertInto("asian_media.publications")
                    .values(publicationData)
                    .asStep()
                    .executeUpdate(publicationData)
            )

            // Krok 3: Identyfikator serii na stronie, z której dodajemy - po nim popup rozpozna ją następnym razem
            request.externalId?.let { externalId ->
                val externalIdData = mapOf(
                    "title_id" to titleIdHandle.value(),
                    "site" to externalId.site,
                    "external_id" to externalId.id
                )
                plan.add(
                    db.insertInto("asian_media.title_external_ids")
                        .values(externalIdData)
                        .asStep()
                        .executeUpdate(externalIdData)
                )
            }

            // Wykonanie planu
            when (val result = dbResult { db.executeTransactionPlan(plan) }) {
                is DataResult.Failure -> {
                    call.respond(
                        PublicationAddResponse(
                            success = false,
                            message = "Wystąpił błąd: ${result.error.message}"
                        )
                    )
                }
                is DataResult.Success -> {
                    // Pobierz wynik z pierwszego kroku, używając bezpiecznego uchwytu
                    val newId = result.value.get(titleIdHandle)

                    println("API: Pomyślnie dodano tytuł z ID: $newId. Wysyłanie zdarzenia nawigacyjnego...")
                    openInForm(newId)

                    call.respond(
                        PublicationAddResponse(
                            success = true,
                            newTitleId = newId,
                            message = "Pomyślnie dodano nowy tytuł do bazy."
                        )
                    )
                }
            }
        }
    }

    /**
     * Definiuje endpoint: POST /api/asian-media/novelupdates/list-move
     * Powtarza w bazie przeniesienie serii na liście lektur NovelUpdates: status bierze się z listy,
     * serię znajdujemy po sid, a bez sid - dokładnie po tytule. Nie przełącza aplikacji na formularz,
     * bo przeniesień przychodzi po kilkadziesiąt naraz.
     */
    private fun Route.mirrorNovelUpdatesListMove() {
        post("/novelupdates/list-move") {
            val move = call.receive<NovelUpdatesListMove>()

            val status = novelUpdatesListStatuses[move.listId]
            if (status == null) {
                call.respond(NovelUpdatesListMoveResponse(false, "Lista ${move.listId} nie ma przypisanego statusu, nic nie zapisano"))
                return@post
            }

            // Tak samo jak przy imporcie listy: języki spoza enuma i brak danych dają chiński.
            val language = when (move.org) {
                "kr" -> PublicationLanguage.Korean
                "jp" -> PublicationLanguage.Japanese
                else -> PublicationLanguage.Chinese
            }
            val params = mapOf(
                "sid" to move.sid,
                "title" to move.title.trim(),
                "language" to language,
                "type" to (move.type ?: PublicationType.WebNovel),
                "status" to status
            )

            val response = when (val result = db.rawQuery(NOVEL_UPDATES_LIST_MOVE_SQL).asResult().fetchRowStrict(params)) {
                is DataResult.Failure -> NovelUpdatesListMoveResponse(false, "Błąd bazy: ${result.error.message}")
                is DataResult.Success -> {
                    val row = result.value
                    val outcome: String = row["result"]
                    val title: String = row["title"]
                    when (outcome) {
                        "CREATED" -> NovelUpdatesListMoveResponse(true, "Dodano „$title”: ${status.toDisplayString()}")
                        "LINKED" -> NovelUpdatesListMoveResponse(true, "Podpięto do „$title”: ${status.toDisplayString()}")
                        "UPDATED" -> NovelUpdatesListMoveResponse(true, "„$title”: ${status.toDisplayString()}")
                        else -> {
                            val candidates: List<String> = row["candidates"]
                            NovelUpdatesListMoveResponse(false, "Nie zapisano, „$title” pasuje do: ${candidates.joinToString(", ") { "„$it”" }}")
                        }
                    }
                }
            }
            call.respond(response)
        }
    }

    /**
     * Definiuje endpoint: POST /api/asian-media/link
     * Podpina serię ze strony do tytułu, który już jest w bazie, i otwiera go w formularzu, żeby ustawić
     * status nowej publikacji - tak samo jak po dodaniu.
     */
    private fun Route.linkPublication() {
        post("/link") {
            val request = call.receive<PublicationLinkRequest>()
            val titles = request.titles.filter { it.isNotBlank() && it.isLatinScript() }

            val result = db.transactionResult {
                request.externalId?.let { externalId ->
                    val linkedTo = rawQuery("SELECT title_id FROM asian_media.title_external_ids WHERE site = @site AND external_id = @id")
                        .asResult().fetchField<Int?>("site" to externalId.site, "id" to externalId.id)
                        .getOrElse { return@transactionResult it }
                    if (linkedTo != null && linkedTo != request.titleId) {
                        return@transactionResult DataResult.Success(
                            PublicationLinkResponse(false, "Ta seria jest już podpięta do innego tytułu (id $linkedTo), nic nie zmieniono")
                        )
                    }
                    if (linkedTo == null) {
                        rawQuery("INSERT INTO asian_media.title_external_ids (title_id, site, external_id) VALUES (@titleId, @site, @id)")
                            .asResult().executeUpdate("titleId" to request.titleId, "site" to externalId.site, "id" to externalId.id)
                            .getOrElse { return@transactionResult it }
                    }
                }

                val addedPublication = rawQuery(
                    """
                    INSERT INTO asian_media.publications (title_id, publication_type, status, track_progress)
                    VALUES (@titleId, @type, @status, false)
                    ON CONFLICT (title_id, publication_type) DO NOTHING
                    """
                ).asResult().executeUpdate("titleId" to request.titleId, "type" to request.type, "status" to PublicationStatus.Trash)
                    .getOrElse { return@transactionResult it }

                val addedTitles = appendTitles(request.titleId, titles).getOrElse { return@transactionResult it }

                val details = listOfNotNull(
                    "dodano publikację: ${request.type.toDisplayString()}".takeIf { addedPublication > 0 },
                    "dopisane tytuły: $addedTitles".takeIf { addedTitles > 0 }
                )
                DataResult.Success(PublicationLinkResponse(true, (listOf("Podpięto") + details).joinToString(", ")))
            }

            when (result) {
                is DataResult.Failure -> call.respond(PublicationLinkResponse(false, "Wystąpił błąd: ${result.error.message}"))
                is DataResult.Success -> {
                    if (result.value.success) openInForm(request.titleId)
                    call.respond(result.value)
                }
            }
        }
    }

    /**
     * Definiuje endpoint: POST /api/asian-media/open
     * Otwiera tytuł w formularzu aplikacji - z popupu, gdy seria już jest w bazie.
     */
    private fun Route.openTitle() {
        post("/open") {
            openInForm(call.receive<TitleOpenRequest>().titleId)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    private fun titleByExternalId(externalId: ExternalId): Pair<Int, String>? =
        db.rawQuery(
            """
            SELECT t.id, t.titles[1] AS main_title
            FROM asian_media.title_external_ids e
                     JOIN asian_media.titles t ON t.id = e.title_id
            WHERE e.site = @site
              AND e.external_id = @id
            """
        ).asResult().fetchRow("site" to externalId.site, "id" to externalId.id)
            .onFailure { println("Błąd wyszukiwania po identyfikatorze serii: ${it.message}") }
            .getOrNull()
            ?.let { row -> row.get<Int>("id") to row.get<String>("main_title") }

    private fun publicationsOf(titleId: Int): List<PublicationSummary> =
        db.rawQuery("SELECT publication_type, status FROM asian_media.publications WHERE title_id = @id ORDER BY publication_type")
            .asResult().fetchRows("id" to titleId)
            .onFailure { println("Błąd odczytu publikacji tytułu $titleId: ${it.message}") }
            .getOrNull().orEmpty()
            .map { row -> PublicationSummary(row["publication_type"], row["status"]) }

    /** Zwraca, ile tytułów przybyło; porównanie bez względu na wielkość liter, kolejność podanych zostaje. */
    private fun appendTitles(titleId: Int, titles: List<String>): DataResult<Int> {
        if (titles.isEmpty()) return DataResult.Success(0)
        return db.rawQuery(
            """
            UPDATE asian_media.titles t
            SET titles = t.titles || missing.titles
            FROM (SELECT array_agg(n.title ORDER BY n.ord) AS titles
                  FROM (SELECT DISTINCT ON (lower(x.title)) x.title, x.ord
                        FROM unnest(@titles) WITH ORDINALITY AS x (title, ord)
                        ORDER BY lower(x.title), x.ord) n
                  WHERE NOT EXISTS (SELECT 1
                                    FROM asian_media.titles e,
                                         unnest(e.titles) AS old (title)
                                    WHERE e.id = @titleId
                                      AND lower(old.title) = lower(n.title))) missing
            WHERE t.id = @titleId
              AND missing.titles IS NOT NULL
            RETURNING cardinality(missing.titles)
            """
        ).asResult().fetchField<Int?>("titleId" to titleId, "titles" to titles.withPgType(PgStandardType.TEXT_ARRAY))
            .map { it ?: 0 }
    }

    private suspend fun openInForm(titleId: Int) {
        NavigationEventBus.post(
            NavigationEvent.Navigate(
                screenId = AsianMediaFeature.ASIAN_MEDIA_FORM_SCREEN_ID,
                payload = mapOf("entityId" to titleId),
                tabId = "asian_media"
            )
        )
    }
}

/**
 * Listy lektur na koncie na NovelUpdates i odpowiadające im statusy. Identyfikatory list są osobne
 * dla każdego konta: 0 Reading, 1 To read, 2 Trash, 3 Read, 4 Readed short, 5 To Read Downloaded.
 */
private val novelUpdatesListStatuses = mapOf(
    0 to PublicationStatus.Reading,
    1 to PublicationStatus.PlanToRead,
    2 to PublicationStatus.Trash,
    3 to PublicationStatus.Completed,
    4 to PublicationStatus.Completed,
    5 to PublicationStatus.PlanToRead,
)

/**
 * Całe przeniesienie w jednej instrukcji, więc bez osobnej transakcji. Kolejność decyzji:
 *
 * - sid już jest w bazie: tylko zmiana statusu,
 * - bez sid, dokładnie jeden tytuł o tej nazwie (bez względu na wielkość liter), jeszcze bez sid z
 *   NovelUpdates, a trafienie jest po tytule głównym albo język się zgadza: podpięcie sid i zmiana statusu,
 * - nic nie pasuje: nowy tytuł z publikacją i sid,
 * - cokolwiek innego jest niejednoznaczne i nic nie zapisuje - jak przy imporcie listy.
 *
 * NovelUpdates to same powieści, więc status zmieniamy tylko publikacjom LN/WN/PN; tytuł, który ma
 * np. samą mangę, dostaje nową publikację zamiast przestawienia mangi.
 */
private const val NOVEL_UPDATES_LIST_MOVE_SQL = """
WITH known AS (
    SELECT title_id
    FROM asian_media.title_external_ids
    WHERE site = 'NOVEL_UPDATES'
      AND external_id = @sid
),
     candidates AS (
         SELECT DISTINCT t.id,
                         t.titles[1]                        AS main_title,
                         t.language,
                         lower(t.titles[1]) = lower(@title) AS main_title_match,
                         EXISTS (SELECT 1
                                 FROM asian_media.title_external_ids e
                                 WHERE e.title_id = t.id
                                   AND e.site = 'NOVEL_UPDATES') AS has_other_sid
         FROM asian_media.title_variants v
                  JOIN asian_media.titles t ON t.id = v.title_id
         WHERE lower(v.title) = lower(@title)
           AND NOT EXISTS (SELECT 1 FROM known)
     ),
     decision AS (
         SELECT CASE
                    WHEN EXISTS (SELECT 1 FROM known) THEN 'UPDATED'
                    WHEN NOT EXISTS (SELECT 1 FROM candidates) THEN 'CREATED'
                    WHEN (SELECT count(*) FROM candidates) = 1
                        AND EXISTS (SELECT 1
                                    FROM candidates
                                    WHERE NOT has_other_sid
                                      AND (main_title_match OR language = @language::asian_media.publication_language))
                        THEN 'LINKED'
                    ELSE 'AMBIGUOUS'
                    END AS result
     ),
     existing AS (
         SELECT title_id AS id FROM known
         UNION ALL
         SELECT id FROM candidates WHERE (SELECT result FROM decision) = 'LINKED'
     ),
     new_title AS (
         INSERT INTO asian_media.titles (titles, language)
             SELECT ARRAY [@title], @language::asian_media.publication_language
             FROM decision
             WHERE result = 'CREATED'
             RETURNING id
     ),
     new_publication AS (
         INSERT INTO asian_media.publications (title_id, publication_type, status, track_progress)
             SELECT id, @type::asian_media.publication_type, @status::asian_media.publication_status, false
             FROM new_title
             UNION ALL
             SELECT id, @type::asian_media.publication_type, @status::asian_media.publication_status, false
             FROM existing e
             WHERE NOT EXISTS (SELECT 1
                               FROM asian_media.publications p
                               WHERE p.title_id = e.id
                                 AND p.publication_type IN ('LIGHT_NOVEL', 'WEB_NOVEL', 'PUBLISHED_NOVEL'))
     ),
     new_sid AS (
         INSERT INTO asian_media.title_external_ids (title_id, site, external_id)
             SELECT id, 'NOVEL_UPDATES'::asian_media.source_site, @sid
             FROM new_title
             UNION ALL
             SELECT id, 'NOVEL_UPDATES', @sid
             FROM existing
             WHERE (SELECT result FROM decision) = 'LINKED'
     ),
     updated_status AS (
         UPDATE asian_media.publications
             SET status = @status::asian_media.publication_status
             WHERE title_id IN (SELECT id FROM existing)
               AND publication_type IN ('LIGHT_NOVEL', 'WEB_NOVEL', 'PUBLISHED_NOVEL')
     )
SELECT d.result,
       coalesce((SELECT t.titles[1] FROM asian_media.titles t WHERE t.id = (SELECT id FROM existing)), @title) AS title,
       (SELECT array_agg(main_title ORDER BY main_title) FROM candidates)                                       AS candidates
FROM decision d
"""