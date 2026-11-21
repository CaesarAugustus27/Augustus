package io.github.octaviusframework.form.control.type.selection.dropdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import io.github.octaviusframework.form.control.base.ControlAction
import io.github.octaviusframework.form.control.base.ControlContext
import io.github.octaviusframework.form.control.base.ControlDependency
import io.github.octaviusframework.form.control.base.ControlState
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.localization.Tr
import org.octavius.theme.FormSpacing

/**
 * How many pixels before the end of the menu loading of the next page starts.
 * Roughly two items, so the list catches up before the user sees the end.
 */
private const val LoadMoreThresholdPx = 100

/**
 * Paging position for one search, replaced as a whole.
 *
 * Keeping this as one object rather than four separate state variables is deliberate: loading the first
 * page and loading further ones are two independent effects, and with separate variables the second could
 * start with a page number still belonging to the previous search. [query] decides what this position
 * belongs to - as long as it does not match the text in the search field, loading more is off, regardless
 * of the order in which the effects start.
 *
 * `null` in [query] is the initial state, which matches no search - including the empty one.
 */
private data class Paging<T>(
    val query: String? = null,
    val options: List<DropdownOption<T>> = emptyList(),
    val nextPage: Long = 0,
    val hasMore: Boolean = false
)

/**
 * Abstract base of dropdown controls that load data asynchronously, support searching and load further
 * pages while scrolling.
 */
abstract class AsyncPaginatedDropdownControl<T : Any>(
    label: String?,
    required: Boolean?,
    dependencies: Map<String, ControlDependency<*>>?,
    actions: List<ControlAction<T>>?
) : DropdownControlBase<T>(label, required, dependencies, actions) {

    /**
     * Subclasses implement this to provide the data of a given page.
     * It is `suspend` because the loading is asynchronous.
     */
    protected abstract suspend fun loadPage(searchQuery: String, page: Long): DropdownPage<T>

    /**
     * How long to wait after the last typed character before the query goes out.
     *
     * Zero by default, because a local database answers faster than the wait would take - debounce would only
     * delay a result that arrives in time anyway. A source where every query costs overrides it: an API over
     * the network takes seconds and can answer 429.
     */
    protected open val debounceMs: Long = 0

    @Composable
    override fun ColumnScope.RenderMenuItems(
        controlContext: ControlContext,
        scope: CoroutineScope,
        controlState: ControlState<T>,
        menuScrollState: ScrollState,
        closeMenu: () -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var paging by remember { mutableStateOf(Paging<T>()) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // Whether the paging position describes what is currently typed in the search field.
        // Until it does, loading is shown and no further pages are loaded.
        val isCurrent = paging.query == searchQuery

        // The first page, and reloading from scratch on every change of the search.
        // The effect is cancelled by the next character, so results do not come back in the wrong order.
        // The same cancellation turns the delay into a debounce: another character during `delay` kills the
        // waiting effect before the query even goes out. An empty field loads right away.
        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotEmpty() && debounceMs > 0) delay(debounceMs)
            loadError = null
            try {
                val page = loadPage(searchQuery, 0)
                menuScrollState.scrollTo(0)
                paging = Paging(searchQuery, page.options, nextPage = 1, hasMore = page.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A message in the menu, not a dialog: with a network source a failed query happens while
                // typing, and a modal would then close the form under the user.
                loadError = e.message ?: e.toString()
            }
        }

        // Loading further pages from the menu's scroll position.
        //
        // `maxValue == 0` means the content does not fill the menu yet - then more is loaded too, because
        // otherwise a short first page gives nothing to scroll and the list gets stuck at the start.
        LaunchedEffect(menuScrollState, searchQuery) {
            snapshotFlow { Triple(menuScrollState.value, menuScrollState.maxValue, paging) }
                .collect { (position, maxPosition, current) ->
                    val atEnd = maxPosition == 0 || position >= maxPosition - LoadMoreThresholdPx
                    if (current.query == searchQuery && current.hasMore && atEnd && !isLoadingMore) {
                        isLoadingMore = true
                        try {
                            val page = loadPage(searchQuery, current.nextPage)
                            paging = current.copy(
                                options = current.options + page.options,
                                nextPage = current.nextPage + 1,
                                hasMore = page.hasMore
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            loadError = e.message ?: e.toString()
                            // Turn loading off. The scroll position has not changed, so the same condition
                            // would be met immediately and the same query would go out in a loop. Only a
                            // change of the search turns it back on.
                            paging = current.copy(hasMore = false)
                        } finally {
                            isLoadingMore = false
                        }
                    }
                }
        }

        // 1. Search field
        SearchField(
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it }
        )

        // 2. The actual menu content
        // Until the new results arrive, the previous ones stay visible with an indicator below them.
        // With a local database there is no visible difference, but with a network query the list would
        // disappear for a few seconds on every character. A big indicator instead of the list only when
        // there is nothing to show. Loading more from a stale position is off anyway (see above).
        MenuContent(
            options = paging.options,
            isRequired = required ?: false,
            isLoadingFirstPage = !isCurrent && paging.options.isEmpty() && loadError == null,
            isLoadingMore = isLoadingMore || (!isCurrent && paging.options.isNotEmpty() && loadError == null),
            errorMessage = loadError,
            onOptionSelected = { selectedOption ->
                controlState.value.value = selectedOption?.value
                // The menu knows the label of the selected item, so it is stored right away - no reason to
                // ask the database for a text already at hand.
                controlState.displayText.value = selectedOption?.displayText
                // The payload exists only here, on selection from the menu. An action like "fill the rest
                // from the selected row" must not get it when the form loads - it would then overwrite the
                // saved data with whatever is currently in the dictionary.
                executeActions(controlContext, selectedOption?.value, scope, payload = selectedOption?.payload)
                closeMenu()
            }
        )
    }

    /**
     * Renders the search field inside the menu.
     */
    @Composable
    private fun SearchField(
        searchQuery: String,
        onQueryChange: (String) -> Unit
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(Tr.Search.placeholder()) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FormSpacing.dropdownPaddingHorizontal,
                    vertical = FormSpacing.dropdownPaddingVertical
                ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = Tr.Search.search()
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = Tr.Search.clear()
                        )
                    }
                }
            }
        )
    }

    /**
     * Renders the main content of the menu.
     *
     * Loading the first page replaces the list, loading another one adds an indicator below it - otherwise
     * the list would disappear every time a batch is added.
     */
    @Composable
    private fun MenuContent(
        options: List<DropdownOption<T>>,
        isRequired: Boolean,
        isLoadingFirstPage: Boolean,
        isLoadingMore: Boolean,
        errorMessage: String?,
        onOptionSelected: (DropdownOption<T>?) -> Unit
    ) {
        if (errorMessage != null) {
            DropdownMenuItem(
                enabled = false,
                text = { Text(errorMessage, color = MaterialTheme.colorScheme.error) },
                onClick = {}
            )
            HorizontalDivider()
        }

        if (isLoadingFirstPage) {
            LoadingIndicator()
            return
        }

        OptionsList(
            options = options,
            isRequired = isRequired,
            onOptionSelected = onOptionSelected
        )

        if (isLoadingMore) {
            LoadingIndicator()
        }
    }

    /**
     * Renders the list of options to choose from.
     */
    @Composable
    private fun OptionsList(
        options: List<DropdownOption<T>>,
        isRequired: Boolean,
        onOptionSelected: (DropdownOption<T>?) -> Unit
    ) {
        // The "null" option (no selection), only if the control is not required
        if (!isRequired) {
            DropdownMenuItem(
                text = { Text(FormTr.Form.Dropdown.noSelection()) },
                onClick = { onOptionSelected(null) }
            )
            HorizontalDivider()
        }

        if (options.isEmpty()) {
            DropdownMenuItem(
                enabled = false,
                text = {
                    Text(
                        FormTr.Form.Dropdown.noResults(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                onClick = {}
            )
        } else {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayText) },
                    onClick = { onOptionSelected(option) }
                )
            }
        }
    }

    /**
     * Renders the loading indicator.
     */
    @Composable
    private fun LoadingIndicator() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FormSpacing.sectionPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
