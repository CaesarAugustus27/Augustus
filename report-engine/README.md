# Report Engine

<div align="center">

**A dynamic data table framework for Compose Multiplatform**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/report-engine/)

</div>

---

## Overview

Report Engine turns a SQL query into an interactive table. You describe the query, the columns and the
actions in a structure builder; the engine derives filters, sorting, pagination, column management and
persistent layouts from that description — including the WHERE clause it sends back to PostgreSQL.

The base query is never rewritten. It is wrapped in a subquery, so filters, sorting and paging apply to
its result set and any valid `SELECT` (joins, aggregates, `GROUP BY`, generated series) works as a source.

## Features

- **Declarative structure** — query, columns and actions in a single builder class
- **Typed columns** — string, number, boolean, enum, date/time, interval, color, list
- **Filters that write SQL** — each column type builds its own parameterised `QueryFragment`
- **Explicit NULL semantics** — every filter can ignore, include or exclude nulls
- **Quick search** — across all filterable columns, or your own query fragment
- **Multi-column sorting** — ordered, drag-reorderable sort chips
- **Column management** — visibility and order, adjusted at runtime
- **Saved layouts** — filters, sorting, visibility and page size persisted per report in the database
- **Server-side pagination** — `COUNT` + `LIMIT`/`OFFSET`, never loads the full result set

## Column Types

All data columns live in `org.octavius.report.column.type`.

| Column                     | Expected value                                       | Filter operators                                                 |
|----------------------------|------------------------------------------------------|------------------------------------------------------------------|
| `StringColumn`             | `String`                                             | exact, not exact, starts with, ends with, contains, not contains |
| `NumberColumn<T : Number>` | any `Number`                                         | `=`, `≠`, `<`, `≤`, `>`, `≥`, range                              |
| `BooleanColumn`            | `Boolean`                                            | true / false / any                                               |
| `EnumColumn<T>`            | enum implementing `EnumWithFormatter`                | multi-select, include or exclude the selection                   |
| `DateTimeColumn<T>`        | `LocalDate`, `LocalDateTime`, `LocalTime`, `Instant` | `=`, `≠`, before, before or equal, after, after or equal, range  |
| `IntervalColumn`           | `kotlin.time.Duration`                               | `=`, `≠`, `<`, `≤`, `>`, `≥`, range                              |
| `ColorColumn`              | hex `String` (`#RRGGBB` / `#AARRGGBB`)               | text filter, renders a color swatch                              |
| `MultiRowColumn`           | `List<T>`                                            | wrapped column's filter, switched to list mode                   |

`NumberColumn` and `DateTimeColumn` are generic and need a `KClass` plus a parser/adapter. Factory
functions cover the usual cases:

| Numbers                                                           | Date and time                                                           |
|-------------------------------------------------------------------|-------------------------------------------------------------------------|
| `IntegerColumn`, `LongColumn`, `DoubleColumn`, `BigDecimalColumn` | `DateColumn`, `LocalDateTimeColumn`, `LocalTimeColumn`, `InstantColumn` |

Two more columns are added by the engine itself and never declared by hand — see [Actions](#actions).

## Quick Start

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
        "status" to EnumColumn(
            header = BooksTr.Report.status(),
            enumClass = ReadingStatus::class
        ),
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

Map keys are the column names produced by the query — they are used verbatim in `WHERE` and `ORDER BY`,
and as the identity of a column in saved layouts. `header` is a plain `String`; headers in this project
come from the i18n layer (`Tr.…()`).

Columns not listed in `buildColumns()` are still fetched and available in `rowData` (`id` above), they
are simply not rendered.

### 2. Create the screen

`ReportView` is a plain composable taking a `ReportHandler` — the engine knows nothing about screens, titles or
navigation. The application wraps it in whatever its own router expects:

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

`ComponentScreen` and `AppRouter` come from the host application's navigation module, not from this engine.

`ReportHandler` builds the structure, restores the saved default layout, and owns the state. `ReportView`
emits `ReportEvent.Initialize` on first composition and cancels pending queries when it leaves composition.

## Column Width

Data columns are laid out by weight, expressed as a plain `Float` (default `1f`):

```kotlin
"title_pl" to StringColumn(header = ..., width = 2f)  // twice the share of a width = 1f column
"language" to EnumColumn(header = ..., enumClass = ..., width = 1f)
```

Internally this becomes `ColumnWidth.Flexible(weight)`. The fixed variant, `ColumnWidth.Fixed(Dp)`, is
reserved for the engine's own action columns.

## Filtering

Filterable columns create their filter lazily; the filter's state lives in `ReportState.filterData`,
keyed by column name. Clicking a column header opens its filter popup, and an active filter marks the
header with an icon.

Each filter builds a parameterised `QueryFragment`; the fragments are joined with `AND` and applied to
the wrapped base query, so nothing is interpolated into SQL by hand.

### NULL handling

Every filter carries a `NullHandling` mode, independent of its value:

| Mode      | Effect                                                                           |
|-----------|----------------------------------------------------------------------------------|
| `Ignore`  | no null condition (default)                                                      |
| `Include` | `(<filter> OR col IS NULL)`, or just `col IS NULL` when no value is set          |
| `Exclude` | `(<filter> AND col IS NOT NULL)`, or just `col IS NOT NULL` when no value is set |

Because of the last two, a filter with an empty value can still be active.

### List mode

Columns wrapped with `asList()` filter against an array column and expose a `FilterMode`:
`ListAny` matches when any element passes, `ListAll` when every element does.

```kotlin
"publication_type" to EnumColumn(
    header = AsianMediaTr.Report.publicationType(),
    enumClass = PublicationType::class,
    width = 1.5f
).asList(9)  // renders up to 9 elements per cell, separated by dividers
```

### Quick search

The search bar above the table runs a separate fragment. By default it matches
`column::text ILIKE %query%` across every filterable column; override `buildQuickSearch` for anything
smarter:

```kotlin
override fun buildQuickSearch(searchQuery: String): QueryFragment =
    "title_id IN (SELECT title_id FROM asian_media.title_variants WHERE title % @searchQuery)" withParam
        ("searchQuery" to searchQuery)
```

## Sorting and Column Management

Both live in the collapsible panel above the table — headers are reserved for filters.

- **Columns** — toggle visibility, drag to reorder. Only columns from `buildColumns()` are manageable;
  action columns are fixed.
- **Sorting** — add a column to the sort, flip its direction, drag the chips to change precedence. The
  chip order is the `ORDER BY` order.

Page size is chosen in the pagination bar (10 / 20 / 50 / 100 / 200; default 50). Any change to
filtering, search or sorting resets to the first page.

## Actions

```kotlin
// Dropdown menu (⋮) on every row
override fun buildRowActions(): List<ReportRowAction> = listOf(
    ReportRowAction(ReportTr.Report.Actions.edit(), Icons.Default.Edit) {
        AppRouter.navigateTo(BookFormScreen.create(rowData["id"] as Int))
    },
    ReportRowAction(Tr.Action.remove(), Icons.Default.Delete) {
        coroutineScope.launch {
            deleteBook(rowData["id"] as Int)
            onEvent(ReportEvent.Initialize)  // refresh
        }
    }
)

// Single icon button on every row, for the one action you use most
override fun buildDefaultRowAction(): ReportRowAction = ReportRowAction(...) { ... }

// Toolbar "add" menu
override fun buildMainActions(): List<ReportMainAction> = listOf(
    ReportMainAction(BooksTr.Report.newBook(), Icons.Default.Add) { ... }
)
```

A row action's lambda runs with `ReportActionContext` as receiver:

| Member           | Use                                                                                       |
|------------------|-------------------------------------------------------------------------------------------|
| `rowData`        | `Map<String, Any?>` of the clicked row — every selected column, not only the visible ones |
| `reportState`    | current filters, sorting, pagination, data                                                |
| `onEvent`        | send a `ReportEvent`, e.g. to reload after a mutation                                     |
| `coroutineScope` | supervised scope for asynchronous work; a failed coroutine ends in the error dialog       |

An exception thrown by a row or main action is logged and shown in the global error dialog instead of taking
the window down (`showUnhandledError` from `ui-core`).

`buildRowActions()` and `buildDefaultRowAction()` generate the `_actions` and `_quick_action` columns.
They are prepended to the column list, excluded from column management, and never filtered or sorted.

## Saved Layouts

A layout captures visible columns, column order, sort order, page size and every filter's serialised
state. `ReportConfigurationManager` stores them in `public.report_configurations`, keyed by the report
name from `getReportName()` plus the layout name:

```kotlin
val manager = ReportConfigurationManager()

manager.saveConfiguration(configuration)        // upsert on (name, report_name)
manager.listConfigurations("books_books")       // defaults first, then alphabetical
manager.loadDefaultConfiguration("books_books") // applied automatically on report open
manager.deleteConfiguration("Wide layout", "books_books")
```

Users manage these through the configuration dialog in the search bar. The layout marked as default is
applied by `ReportHandler` at startup, so a report can open with filters already in place.

The table and its supporting PostgreSQL types (`filter_config`, `sort_direction`, `sort_configuration`)
ship as a migration inside this module — no setup needed beyond running migrations.

## Localization

The engine owns its strings. `src/commonMain/resources/i18n/{en,pl}.json` are compiled by the
[Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n) plugin into
`org.octavius.report.localization.ReportTr`, generated from this module alone:

```kotlin
octaviusI18n {
    generators {
        create("report") {
            targetPackage = "org.octavius.report.localization"
            objectName = "ReportTr"
        }
    }
}
```

Everything the engine renders on its own — filter popups, pagination, column management, the saved-layout
dialog — resolves through `ReportTr`, never through the host application's translation object. `ReportTr`
is public, so labels a report needs but should not have to spell out again can be reused from it:
`ReportTr.Report.Actions.edit()` for the usual edit action, `Tr.Action.remove()` for a delete one.

A new language is a new `<lang>.json` in the same directory. Keys are unioned across languages, so a
partial file still builds: a key missing from the selected language renders as its raw path, and a
language with no file at all falls back to English. The active language is global —
`OctaviusI18n.currentLanguage` — and shared with every other translation object in the application.
## State and Data Flow

The engine follows a unidirectional loop. `ReportState` is immutable and exposed as a `StateFlow`;
`ReportHandler` is the only thing that writes to it.

```
UI ──ReportEvent──▶ ReportHandler ──reduce──▶ ReportState ──▶ recomposition
                          │                        │
                          └── if triggersDataReload() ──▶ ReportDataManager ──▶ PostgreSQL
```

`ReportEvent` covers `Initialize`, `SearchQueryChanged`, `PageChanged`, `PageSizeChanged`,
`SortOrderChanged`, `FilterChanged`, `ClearFilter`, `ColumnVisibilityChanged`, `ColumnOrderChanged` and
`ApplyConfiguration`. Each one declares via `triggersDataReload()` whether it needs a round trip —
column visibility and reordering are pure UI changes and never hit the database.

Fetching is cancellable: a new request cancels the in-flight one. `ReportDataManager` first runs a
`COUNT(*)` over the filtered subquery to compute pagination, skips the main query entirely when the count
is zero, and reports failures as `ReportDataResult.Failure` — surfaced through `GlobalDialogManager`
rather than thrown.

## Architecture

```
report-engine/
├── component/
│   ├── ReportStructure.kt          # Structure + ReportStructureBuilder (template method)
│   ├── ReportState.kt              # Immutable runtime state
│   ├── ReportHandler.kt            # Event reduction, data fetching, configuration
│   ├── ReportDataManager.kt        # WHERE/ORDER BY building, COUNT + paged query
│   └── ReportView.kt               # Composable wiring the UI together
│
├── column/
│   ├── ReportColumn.kt             # Base class: header rendering, filter popup
│   └── type/                       # String, Number, Boolean, Enum, DateTime, Interval, Color, MultiRow
│       └── special/                # Action/QuickAction
│
├── filter/
│   ├── Filter.kt                   # Base: SQL generation, null handling, list mode
│   ├── data/                       # FilterData — serialisable per-filter state
│   └── type/                       # One filter implementation per column type
│
├── configuration/                  # Saved layouts: model, manager, dialog
├── management/                     # Column visibility, ordering, sort chips
├── ui/                             # Search bar, pagination, table rendering
│
└── resources/
    ├── i18n/                       # en/pl translations, generated into `ReportTr`
    └── db/migration/public/        # report_configurations table and PG types (the app's `public` history)
```

The module targets the desktop JVM target only and depends on `ui-core` for theme, dialogs and
drag-and-drop, on [Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql) for
`OctaviusClient` and `QueryFragment`, and on [Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n)
for its translations. `ReportStructureBuilder`, `ReportDataManager` and
`ReportConfigurationManager` obtain `OctaviusClient` through Koin.

It does **not** depend on the application's navigation module. Row actions that navigate — `AppRouter.navigateTo(...)`
in the examples above — are lambdas written in application code; the engine only invokes them.
