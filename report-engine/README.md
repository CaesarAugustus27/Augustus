# Report Engine

<div align="center">

**Data tables for Compose Multiplatform, built from a SQL query**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/report-engine/)

</div>

---

## Overview

A report is a SQL query, a set of columns and a set of actions. The engine turns them into a table with filters, search,
multi-column sorting, column management, saved layouts and pagination on the database side.

The base query is wrapped in a subquery and stays unchanged, so it can be any valid `SELECT`: joins, aggregates,
`GROUP BY`, `generate_series`. Filters, sorting and paging apply to its result.

## Column types

Data columns live in `org.octavius.report.column.type`. Each takes `header`, `width`, `sortable` and `filterable`, apart
from `MultiRowColumn`, which wraps another column through `asList()`.

| Column                     | Expected value                                       | Filter                                                           |
|----------------------------|------------------------------------------------------|------------------------------------------------------------------|
| `StringColumn`             | `String`                                             | exact, not exact, starts with, ends with, contains, not contains |
| `NumberColumn<T : Number>` | any `Number`                                         | `=`, `≠`, `<`, `≤`, `>`, `≥`, range                              |
| `BooleanColumn`            | `Boolean`                                            | true or false                                                    |
| `EnumColumn<T>`            | enum implementing `EnumWithFormatter`                | multi-select, include or exclude the selection                   |
| `DateTimeColumn<T>`        | `LocalDate`, `LocalDateTime`, `LocalTime`, `Instant` | `=`, `≠`, before, before or equal, after, after or equal, range  |
| `IntervalColumn`           | `kotlin.time.Duration`                               | `=`, `≠`, `<`, `≤`, `>`, `≥`, range                              |
| `ColorColumn`              | hex `String` (`#RRGGBB`, `#AARRGGBB`)                | text filter, the cell shows a color swatch                       |
| `MultiRowColumn`           | `List<T>`                                            | the wrapped column's filter in list mode                         |

`NumberColumn` and `DateTimeColumn` need a `KClass` and a parser or adapter. These functions create them for the usual
types:

| Numbers                                                           | Date and time                                                           |
|-------------------------------------------------------------------|-------------------------------------------------------------------------|
| `IntegerColumn`, `LongColumn`, `DoubleColumn`, `BigDecimalColumn` | `DateColumn`, `LocalDateTimeColumn`, `LocalTimeColumn`, `InstantColumn` |

`width` is a weight, `1f` by default. A column with `width = 2f` gets twice the share of a `1f` column.

## Quick start

### 1. Define the report structure

```kotlin
class BooksReportStructureBuilder : ReportStructureBuilder() {

    override fun getReportName(): String = "books_books"

    override fun buildQuery(): QueryFragment = QueryFragment(
        sql = """
            SELECT b.id, b.title_pl, b.status, b.created_at,
                   COALESCE(STRING_AGG(a.name, ', ' ORDER BY a.sort_name), '') AS authors
            FROM books.books b
            LEFT JOIN books.book_to_authors bta ON b.id = bta.book_id
            LEFT JOIN books.authors a ON bta.author_id = a.id
            GROUP BY b.id, b.title_pl, b.status, b.created_at
        """.trimIndent()
    )

    override fun buildColumns(): Map<String, ReportColumn> = mapOf(
        "title_pl" to StringColumn(header = BooksTr.Report.titlePl(), width = 2f),
        "authors" to StringColumn(header = BooksTr.Report.authors()),
        "status" to EnumColumn(header = BooksTr.Report.status(), enumClass = ReadingStatus::class),
        "created_at" to InstantColumn(header = BooksTr.Report.createdAt())
    )

    override fun buildDefaultRowAction(): ReportRowAction = ReportRowAction(
        label = BooksTr.Report.editBook(),
        icon = Icons.Default.Edit
    ) {
        val bookId = rowData["id"] as? Int ?: return@ReportRowAction
        AppRouter.navigateTo(BookFormScreen.create(bookId))
    }

    override fun buildMainActions(): List<ReportMainAction> = listOf(
        ReportMainAction(BooksTr.Report.newBook(), Icons.Default.Add) {
            AppRouter.navigateTo(BookFormScreen.create())
        }
    )
}
```

`getReportName()` identifies the report in saved layouts. The keys of `buildColumns()` must be column names in the
query's result: they go into `WHERE` and `ORDER BY` as they are, and identify the column in saved layouts. Columns left
out of `buildColumns()` are not rendered but stay available in `rowData`, like `id` above.

### 2. Create the screen

`ReportView(handler)` is a composable. In Augustus it goes into a `ComponentScreen` from the `navigation` module:

```kotlin
class BooksReportScreen {
    companion object {
        fun create(): Screen {
            val reportHandler = ReportHandler(BooksReportStructureBuilder())
            return ComponentScreen(BooksTr.Report.title()) { ReportView(reportHandler) }
        }
    }
}

AppRouter.navigateTo(BooksReportScreen.create())
```

`ReportHandler` builds the structure, applies the default saved layout and holds the state. `ReportView` loads the data
on first composition and cancels its fetches when it leaves the composition.

## Filtering

Clicking a column header opens its filter. A header with an active filter shows an icon. Each filter builds a
parameterised `QueryFragment`, and the fragments are joined with `AND` on top of the wrapped query.

### NULL handling

Every filter has a `NullHandling` mode, independent of its value:

| Mode      | Effect                                                                      |
|-----------|-----------------------------------------------------------------------------|
| `Ignore`  | no null condition (default)                                                 |
| `Include` | `(<filter> OR col IS NULL)`, or `col IS NULL` when no value is set          |
| `Exclude` | `(<filter> AND col IS NOT NULL)`, or `col IS NOT NULL` when no value is set |

A filter with no value is therefore active under `Include` and `Exclude`.

### List mode

A column wrapped with `asList()` shows a list in one cell and filters an array column. The filter's `FilterMode` is
`ListAny` (default), which matches when any element passes, or `ListAll`, which matches when every element does.

```kotlin
"publication_type" to EnumColumn(
    header = AsianMediaTr.Report.publicationType(),
    enumClass = PublicationType::class,
    width = 1.5f
).asList(9) // up to 9 elements per cell, 3 by default
```

### Quick search

The search bar above the table adds its own condition. By default it matches `column::text ILIKE %query%` on every
filterable column, joined with `OR`. `buildQuickSearch` replaces that:

```kotlin
override fun buildQuickSearch(searchQuery: String): QueryFragment =
    "title_id IN (SELECT title_id FROM asian_media.title_variants WHERE title % @searchQuery)" withParam
        ("searchQuery" to searchQuery)
```

## Sorting and columns

Both are in the collapsible panel above the table.

- **Columns:** toggle visibility and drag to reorder.
- **Sorting:** add a column, flip its direction, and drag the chips to change precedence. The chip order is the
  `ORDER BY` order.

The page size is chosen in the pagination bar: 10, 20, 50, 100 or 200, with 50 as the default. A change to filters, search,
sorting or page size goes back to the first page.

## Actions

```kotlin
// Menu (⋮) on every row
override fun buildRowActions(): List<ReportRowAction> = listOf(
    ReportRowAction(ReportTr.Report.Actions.edit(), Icons.Default.Edit) {
        AppRouter.navigateTo(BookFormScreen.create(rowData["id"] as Int))
    },
    ReportRowAction(Tr.Action.remove(), Icons.Default.Delete) {
        coroutineScope.launch {
            deleteBook(rowData["id"] as Int)
            onEvent(ReportEvent.Initialize) // reloads the data
        }
    }
)

// Single icon button on every row
override fun buildDefaultRowAction(): ReportRowAction = ReportRowAction(...) { ... }

// "Add" menu in the search bar
override fun buildMainActions(): List<ReportMainAction> = listOf(
    ReportMainAction(BooksTr.Report.newBook(), Icons.Default.Add) { ... }
)
```

`ReportTr` is public, so `ReportTr.Report.Actions.edit()` is available as a label. A row action's lambda runs with
`ReportActionContext` as its receiver:

| Member           | Use                                                                    |
|------------------|------------------------------------------------------------------------|
| `rowData`        | `Map<String, Any?>` of the clicked row, with every column of the query |
| `reportState`    | current filters, sorting, pagination and data                          |
| `onEvent`        | sends a `ReportEvent`, e.g. to reload after a change                   |
| `coroutineScope` | scope for asynchronous work                                            |

An exception from an action, or from a coroutine it launches in `coroutineScope`, ends in the global error dialog and the
window stays open.

`buildRowActions()` and `buildDefaultRowAction()` add the columns `_actions` and `_quick_action` in front of the data
columns. They have a fixed width, can't be filtered or sorted, and don't appear in column management.

## Saved layouts

A layout stores visible columns, column order, sort order, page size and the state of every filter. The configuration
dialog in the search bar manages layouts. The layout marked as default is applied when the report opens, so a report can
start with filters already set.

`ReportConfigurationManager` keeps them in `public.report_configurations`, keyed by layout name and report name:

```kotlin
val manager = ReportConfigurationManager()

manager.saveConfiguration(configuration)        // insert or update on (name, report_name)
manager.listConfigurations("books_books")       // default first, then by name
manager.loadDefaultConfiguration("books_books")
manager.deleteConfiguration("Wide layout", "books_books")
```

Applying a layout drops the filters, sorting and visibility of columns the report no longer has. Columns added since the
layout was saved end up last and hidden.

The table and its types (`filter_config`, `sort_direction`, `sort_configuration`) come from a migration in this module,
in the `public` sequence of the application.

## Localization

The engine's strings are in `src/commonMain/resources/i18n/{en,pl}.json`, which
[Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n) compiles into
`org.octavius.report.localization.ReportTr`. Strings shared with the rest of the UI come from `Tr` in `ui-core`.

## State and data flow

`ReportState` is immutable and exposed as a `StateFlow`. Only `ReportHandler` writes to it.

```
UI ──ReportEvent──▶ ReportHandler ──reduce──▶ ReportState ──▶ recomposition
                          │                        │
                          └── if triggersDataReload() ──▶ ReportDataManager ──▶ PostgreSQL
```

| `ReportEvent`                                        | Reloads data |
|------------------------------------------------------|--------------|
| `Initialize`, `ApplyConfiguration`                   | yes          |
| `SearchQueryChanged`, `FilterChanged`, `ClearFilter` | yes          |
| `PageChanged`, `PageSizeChanged`, `SortOrderChanged` | yes          |
| `ColumnVisibilityChanged`, `ColumnOrderChanged`      | no           |

A new fetch cancels the previous one, whose result is then dropped. `ReportDataManager` first runs `COUNT(*)` on the
filtered subquery and skips the main query when the count is zero. A database failure comes back as
`ReportDataResult.Failure` and shows up in the global dialog.

## Source layout

```
report-engine/
├── component/
│   ├── ReportStructure.kt          # ReportStructure, ReportStructureBuilder
│   ├── ReportState.kt              # Immutable state
│   ├── ReportHandler.kt            # Events, data fetching, applying layouts
│   ├── ReportDataManager.kt        # WHERE and ORDER BY, COUNT and the paged query
│   └── ReportView.kt               # Composable with the whole report
│
├── column/
│   ├── ReportColumn.kt             # Header, filter popup, cell rendering
│   └── type/                       # One file per column type
│       └── special/                # Action columns
│
├── filter/
│   ├── Filter.kt                   # SQL fragment, NULL handling, list mode
│   ├── data/type/                  # FilterData, the serialisable state of each filter
│   └── type/                       # One filter per column type
│
├── configuration/                  # Saved layouts: model, manager, dialog
├── management/                     # Column visibility and order, sort chips
├── ui/                             # Search bar, pagination, table
│
└── resources/
    ├── i18n/                       # en and pl translations
    └── db/migration/public/        # report_configurations and its types
```

The module targets the desktop JVM. It depends on `ui-core` for the theme, dialogs and drag and drop, on
[Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql) for `OctaviusClient`, which it gets
through Koin, and for `QueryFragment`, and on Octavius I18n for translations.
