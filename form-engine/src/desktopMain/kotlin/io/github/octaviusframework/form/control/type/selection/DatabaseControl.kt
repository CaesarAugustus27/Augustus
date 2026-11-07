package io.github.octaviusframework.form.control.type.selection

import io.github.octaviusframework.client.DataResult
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.client.query.QueryFragment
import io.github.octaviusframework.client.query.withParam
import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.octavius.dialog.ErrorDialogConfig
import org.octavius.dialog.GlobalDialogManager
import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.type.selection.dropdown.AsyncPaginatedDropdownControl
import io.github.octaviusframework.form.control.type.selection.dropdown.DropdownOption
import io.github.octaviusframework.form.control.type.selection.dropdown.DropdownPage

/**
 * A control for choosing a database record from a dropdown list.
 *
 * The source is any query. The control wraps it in a subquery and adds searching, sorting and paging,
 * never rewriting it - the same contract as `buildQuery()` in report-engine. Joins, a fixed filter or a
 * computed item text therefore go into the SQL, not into the control's parameters:
 *
 * ```kotlin
 * "author_id" to DatabaseControl(
 *     label = BooksTr.Form.author(),
 *     query = QueryFragment("SELECT id, name FROM books.authors"),
 *     displayColumn = "name"
 * )
 * ```
 *
 * The control's value is the [idColumn] of the selected row. The whole row - including the columns the
 * control does not show - goes to `ActionContext.payload` on selection, so an action can fill other
 * controls from it.
 *
 * @param query Source query. Its parameters are attached to every call.
 * @param displayColumn Result column shown as the item text.
 * @param idColumn Result column that is the control's value.
 * @param orderBy Sorting of the list, by [displayColumn] by default. [idColumn] is always appended at the
 *   end as a tie-breaker: without it paging with `OFFSET` is not deterministic, and when further pages are
 *   appended an item with a repeated text at a page boundary could show up twice or not at all.
 * @param searchFilter Search condition for the typed text. No lambda, or `null` from it, means the default
 *   `displayColumn::text ILIKE %text%`. The same shape as `buildQuickSearch` in report-engine.
 * @param pageSize Number of items loaded at once.
 */
class DatabaseControl(
    label: String?,
    private val query: QueryFragment,
    private val displayColumn: String,
    private val idColumn: String = "id",
    private val orderBy: String = displayColumn,
    private val searchFilter: ((String) -> QueryFragment?)? = null,
    private val pageSize: Long = 10,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<Int>>? = null,
) : AsyncPaginatedDropdownControl<Int>(
    label, required, dependencies, actions
), KoinComponent {

    private val db: OctaviusClient by inject()

    override fun acceptsValue(value: Any) = value is Int

    /**
     * Called once per value, outside rendering, and the result is stored in `ControlState.displayText`.
     *
     * There is no cache here and there should not be: one control instance serves all repeatable rows (see
     * `RepeatableControl.registerChildrenInGlobalMap`), so a field on the instance would mix rows up. The
     * proper cache is the state keyed by path.
     */
    override suspend fun resolveDisplayText(value: Int): String? = withContext(OctaviusDispatchers.Virtual) {
        // Nullable T on purpose: the row may have disappeared from under the saved id, and with a non-null
        // type a missing row and NULL throw an exception that .asResult() does not turn into a Failure.
        val result = db.select(displayColumn).fromSubquery(query.sql).where("$idColumn = @selectedId")
            .asResult().fetchField<String?>(query.params + ("selectedId" to value))

        when (result) {
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                null
            }

            is DataResult.Success<String?> -> result.value
        }
    }

    override suspend fun loadPage(searchQuery: String, page: Long): DropdownPage<Int> {
        return withContext(OctaviusDispatchers.Virtual) {
            val search = buildSearchFilter(searchQuery)

            // Fetch one row more than the page: if it came, there is more to load.
            val optionsResult = db.select("*").fromSubquery(query.sql)
                .where(search.sql)
                .orderBy("$orderBy, $idColumn")
                .limit(pageSize + 1)
                .offset(page * pageSize)
                .asResult().fetchObjects<Map<String, Any?>>(params = query.params + search.params)

            when (optionsResult) {
                is DataResult.Success -> {
                    val rows = optionsResult.value
                    val mappedOptions = rows.take(pageSize.toInt()).map { row ->
                        DropdownOption(
                            value = row[idColumn] as Int,
                            displayText = row[displayColumn] as String,
                            payload = row
                        )
                    }
                    DropdownPage(mappedOptions, hasMore = rows.size > pageSize)
                }

                is DataResult.Failure -> {
                    GlobalDialogManager.show(ErrorDialogConfig(optionsResult.error))
                    DropdownPage(emptyList(), hasMore = false)
                }
            }
        }
    }

    private fun buildSearchFilter(searchQuery: String): QueryFragment {
        if (searchQuery.isBlank()) return QueryFragment("")

        return searchFilter?.invoke(searchQuery)
            ?: ("$displayColumn::text ILIKE @searchQuery" withParam ("searchQuery" to "%$searchQuery%"))
    }
}
