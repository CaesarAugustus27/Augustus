package org.octavius.modules.sandbox.form.nested

import io.github.octaviusframework.form.component.FormDataManager
import io.github.octaviusframework.form.control.base.FormResultData
import io.github.octaviusframework.form.control.base.getCurrentAs
import io.github.octaviusframework.form.control.type.repeatable.RepeatableResultValue
import org.octavius.modules.sandbox.localization.SandboxTr
import org.octavius.ui.snackbar.SnackbarManager

/**
 * Wczytuje legion jednym zapytaniem z trzema zagnieżdżonymi `mapRelatedList`. Sandbox nie ma tabel, więc każdy
 * poziom to podzapytanie na `generate_series`. Kohorta `c` ma `c` centurii, a centuria `n` ma `n - 1`
 * kontuberniów, więc pierwsza centuria każdej kohorty wraca z pustą listą.
 */
class SandboxNestedFormDataManager : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? = loadData(1) {
        from("(SELECT 1 AS id, 'Legio X Gemina' AS legion)", "s")
        map("legion", "s.legion")

        mapRelatedList("cohorts") {
            from(
                """(SELECT c AS id, 1 AS legion_id, 'Cohors ' || (ARRAY['I', 'II', 'III'])[c] AS name,
                    c <> 2 AS show_strength
                    FROM generate_series(1, 3) c)""",
                "l1"
            )
            linkedBy("l1.legion_id")
            orderBy("l1.id")
            map("cohort_name", "l1.name")
            map("show_strength", "l1.show_strength")

            mapRelatedList("centuries") {
                from(
                    """(SELECT c * 10 + n AS id, c AS cohort_id, n, 'Centurio ' || c || '.' || n AS centurion
                        FROM generate_series(1, 3) c, generate_series(1, c) n)""",
                    "l2"
                )
                linkedBy("l2.cohort_id", "l1.id")
                orderBy("l2.n")
                map("centurion", "l2.centurion")

                mapRelatedList("contubernia") {
                    from(
                        """(SELECT c * 10 + n AS century_id, k, 'Contubernium ' || k AS name, 8 - k AS soldiers
                            FROM generate_series(1, 3) c, generate_series(1, c) n, generate_series(1, n - 1) k)""",
                        "l3"
                    )
                    linkedBy("l3.century_id", "l2.id")
                    orderBy("l3.k")
                    map("contubernium_name", "l3.name")
                    map("soldiers", "l3.soldiers")
                }
            }
        }
    }

    override fun definedFormActions(): Map<String, (formResultData: FormResultData) -> Boolean> = mapOf(
        "save" to { result ->
            val cohorts = result.getCurrentAs<RepeatableResultValue>("cohorts")
            val centuries = cohorts.allCurrentRows.map { it.getCurrentAs<RepeatableResultValue>("centuries") }
            val contubernia = centuries.flatMap { it.allCurrentRows }
                .map { it.getCurrentAs<RepeatableResultValue>("contubernia") }
            SnackbarManager.showMessage(
                SandboxTr.NestedForm.savedMessage(
                    cohorts = changes(listOf(cohorts)),
                    centuries = changes(centuries),
                    contubernia = changes(contubernia)
                )
            )
            true
        }
    )

    // Zmiany jednego poziomu, zsumowane po wszystkich listach tego poziomu.
    private fun changes(lists: List<RepeatableResultValue>): String = SandboxTr.NestedForm.changes(
        added = lists.sumOf { it.addedRows.size },
        modified = lists.sumOf { it.modifiedRows.size },
        deleted = lists.sumOf { it.deletedRows.size }
    )
}
