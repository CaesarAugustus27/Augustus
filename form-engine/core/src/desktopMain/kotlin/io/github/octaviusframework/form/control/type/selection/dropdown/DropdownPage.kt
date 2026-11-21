package io.github.octaviusframework.form.control.type.selection.dropdown

/**
 * One batch of options for a dropdown control that loads data in pages.
 *
 * There is no page count or result count here, and there should not be: to provide them, the source would
 * have to count the whole set, i.e. run a second query next to the one that runs anyway - and while
 * loading during scrolling nobody looks at that number. It is enough to know whether there is more to load,
 * and that shows in the extra row fetched beyond the page size.
 *
 * @param T type of the option values.
 * @property options The options of this page, already trimmed to the page size.
 * @property hasMore Whether there is anything after this page.
 */
data class DropdownPage<T>(
    val options: List<DropdownOption<T>>,
    val hasMore: Boolean
)
