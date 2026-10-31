# Form Engine

<div align="center">

**A declarative, data-driven form framework for Compose Multiplatform**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/form-engine/)

</div>

---

## Overview

Form Engine turns a schema — a map of named controls plus a display order — into a full CRUD form. It renders
the controls, tracks their state, loads and saves data through a small SQL DSL, runs three levels of validation,
and lets one control's value drive another's visibility, requirement, or content.

A control never talks to the database directly. `FormDataManager` loads initial values with a declarative DSL,
and every form action receives a `FormResultData` — each control's current *and* initial value — so `save` /
`delete` / etc. can be written as a couple of `OctaviusClient` calls.

## Features

- **Declarative schema** — controls, content order and action-bar order in one builder class
- **Rich control types** — primitives, dropdowns, selection groups, dates/intervals, files, sections, repeatable rows
- **Dependencies** — show/hide or require a control based on another control's value
- **Cross-control actions** — a control action can read or write any other control by relative (`./`, `../`) or
  wildcard (`*`) path
- **Three-level validation** — per-field, business rules and per-action, each with its own error surface
- **Diffed repeatable rows** — added/modified/deleted rows are separated for you, not just handed back as a flat list
- **Two-way binding** — `ControlState` is Compose state, so edits recompose automatically

## Control Types

| Category | Package | Controls |
|---|---|---|
| **Primitive** | `control.type.primitive` | `StringControl`, `MultilineStringControl`, `CheckboxControl`, `SwitchControl`, `FilePickerControl` |
| **Number** | `control.type.number` | `IntegerControl`, `DoubleControl`, `BigDecimalControl` |
| **DateTime** | `control.type.datetime` | `DateTimeControl`, `IntervalControl` |
| **Selection** | `control.type.selection` | `EnumControl`, `DatabaseControl`, `RadioGroupControl`, `CheckboxGroupControl` |
| **Collection** | `control.type.collection` | `StringListControl` |
| **Container** | `control.type.container` | `SectionControl` |
| **Repeatable** | `control.type.repeatable` | `RepeatableControl` |
| **Action** | `control.type.button` | `ButtonControl` |

`EnumControl` and `DatabaseControl` both build on `AsyncPaginatedDropdownControl` — a search box with paginated
results. `EnumControl` reads its options from a `KClass<T>` whose `T` implements `EnumWithFormatter`;
`DatabaseControl` queries `relatedTable` / `displayColumn` directly, caching the selected row's display text.
`DateTimeControl<T>` is generic over a `DateTimeAdapter<T>`, covering `LocalDate`, `LocalDateTime`, `LocalTime`
and `Instant` from one implementation.

## Quick Start

### 1. Define a Schema

```kotlin
class BookFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(label = null), // hidden — carries the loaded id through to the save action
        "title" to StringControl(label = "Title", required = true),
        "author" to StringControl(label = "Author"),
        "year" to IntegerControl(label = "Year"),
        "status" to EnumControl(label = "Status", enumClass = ReadingStatus::class),

        "save_button" to ButtonControl(
            text = "Save",
            buttonType = ButtonType.Filled,
            actions = listOf(ControlAction {
                if (trigger.triggerAction("save", validates = true)) AppRouter.goBack()
            })
        ),
        "cancel_button" to ButtonControl(
            text = "Cancel",
            buttonType = ButtonType.Outlined,
            // Cancel touches no data, so it never reaches the data manager
            actions = listOf(ControlAction { AppRouter.goBack() })
        )
    )

    // "id" is tracked but never rendered — it's simply absent from contentOrder
    override fun defineContentOrder() = listOf("title", "author", "year", "status")

    override fun defineActionBarOrder() = listOf("cancel_button", "save_button")
}
```

### 2. Implement Data Manager

```kotlin
class BookFormDataManager : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
        return loadData(payload["id"]) {
            from("books", "b")
            map("title")
            map("author")
            map("year")
            map("status")
        }
    }

    override fun definedFormActions(): Map<String, (FormResultData) -> Boolean> = mapOf(
        "save" to { formData -> processSave(formData) }
    )

    private fun processSave(formResultData: FormResultData): Boolean {
        val loadedId = formResultData.getInitial("id")
        val params = mapOf(
            "title" to formResultData.getCurrent("title"),
            "author" to formResultData.getCurrent("author"),
            "year" to formResultData.getCurrent("year"),
            "status" to formResultData.getCurrent("status")
        )

        val result = if (loadedId != null) {
            db.update("books").setValues(params).where("id = @id")
                .asResult().executeUpdate(params + ("id" to loadedId))
        } else {
            db.insertInto("books").values(params).asResult().executeUpdate(params)
        }

        return when (result) {
            is DataResult.Success<*> -> true
            is DataResult.Failure -> {
                // `false` carries the fact of failure, not its content — surface the error here
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }
        }
    }
}
```

`getCurrent` / `getInitial` (and their typed `getCurrentAs<T>` / `getInitialAs<T>` variants) read
`ControlResultData` out of `FormResultData` without the `!!.currentValue as T` boilerplate. A path without a
control, a path through a control that is not a section, or a value that is not a `T` (including `null` for a
non-null `T`, as for a hidden control) throws `FormResultException` with the path.

### 3. Create the Screen

`FormView` is a plain composable taking a `FormHandler` — the engine knows nothing about screens, titles or
navigation. The application wraps it in whatever its own router expects:

```kotlin
class BookFormScreen {
    companion object {
        fun create(bookId: Int? = null): Screen {
            val formHandler = FormHandler(
                formSchemaBuilder = BookFormSchemaBuilder(),
                formDataManager = BookFormDataManager(),
                formValidator = BookFormValidator(),
                payload = mapOf("id" to bookId)
            )
            return ComponentScreen(title = "Books") { FormView(formHandler) }
        }
    }
}

AppRouter.navigateTo(BookFormScreen.create(bookId))
```

`ComponentScreen` and `AppRouter` come from the host application's navigation module, not from this engine.
Anything that can render a composable works just as well — a dialog, a second window, a tab host.

`FormHandler` loads the initial data asynchronously and owns `FormState`, `ErrorManager` and the wiring between
controls; `FormView` shows a spinner while `loadState` is `Loading`, a "could not load" message instead of the
controls once it is `Failed`, and blocks input behind an overlay while an action is running (`actionTriggered`).

`triggerAction` returns `true` when the action ran and reported success. That single boolean is everything the
engine hands back — what happens next (closing the screen, going somewhere else) is decided by the button's
lambda, which is application code and already knows the router.

## Data Loading DSL

The `loadData` function provides a DSL for loading form data:

```kotlin
override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
    return loadData(payload["id"]) {
        // Main table
        from("books", "b")
        map("title", "title") // control name -> db column (default is control name in snake_case)
        map("author")
        map("publication_year", "pub_year")

        // One-to-one relation
        mapOneToOne {
            from("book_details", "bd")
            on("bd.book_id = b.id")
            map("isbn")
            map("page_count") // matches db column 'page_count' automatically
            existenceFlag("has_details", "bd.id")  // boolean flag if relation exists
        }

        // One-to-many relation (for RepeatableControl)
        mapRelatedList("authors") {
            from("book_authors", "ba")
            linkedBy("ba.book_id") // defaults to matching against the main row's "@id"
            orderBy("ba.position") // optional; without it rows come back in no particular order
            map("name")
            map("role")
        }
    }
}
```

Everything compiles down to a single query: `mapOneToOne` becomes a `LEFT JOIN`, `mapRelatedList` becomes a
correlated `ARRAY(SELECT ROW(...))::record[]` subquery — an anonymous record read back as a
`Map<String, Any?>` per row, keys and values alternating, which is what a repeatable control wants and what
no declared type covers. `orderBy` lands inside that subquery verbatim, and the array keeps its order, so the
repeatable shows rows in that order. `id` is assumed as the main table's primary key; override it with `idColumn("uuid")`
if it's named differently. If `id` (the value passed to `loadData`) is `null`, loading is skipped and an empty
map is returned — the natural path for a "new record" form.

If the query fails, or no row matches the `id`, `loadData` shows a dialog with the reason and returns `null`.
`initData` passes it on and the form ends in `FormLoadState.Failed`:

```kotlin
override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
    val loaded = loadBookData(payload["id"]) ?: return null
    return defaults + loaded + payload
}
```

### Initial data must fit the schema

Every key returned by `initData` has to name a control, and every non-null value has to be one the control
accepts (`Control.acceptsValue`). Otherwise loading throws `InitDataException` with the schema path of the
value, e.g. `"basic_info/status"`, or `"publications/status"` for a field of repeatable rows.

| Control | Accepts | Typical PostgreSQL column |
|---|---|---|
| `StringControl`, `MultilineStringControl` | `String` | `text`, `varchar` |
| `IntegerControl` | `Int` | `integer` (`smallint` reads as `Short`, `bigint` as `Long` — neither fits) |
| `DoubleControl` | `Double` | `double precision` (`real` reads as `Float`) |
| `BigDecimalControl` | `BigDecimal` | `numeric` |
| `CheckboxControl`, `SwitchControl` | `Boolean` | `boolean` |
| `DateTimeControl<T>` | the adapter's `valueClass` | `date`, `time`, `timestamp`, `timestamptz` |
| `IntervalControl` | `kotlin.time.Duration` | `interval` (through the application's converter) |
| `EnumControl<T>` | a constant of `T` | the enum type registered with `@PgEnumType` |
| `DatabaseControl` | `Int` | the `integer` key of the related row |
| `RadioGroupControl<T>` | one of its option values | — |
| `CheckboxGroupControl<T>` | a list of its option values | — |
| `StringListControl` | `List<String>` | `text[]` |
| `FilePickerControl<T>` | `String` (`PATH_STRING`) or `ByteArray` (`CONTENT_BYTES`) | `text` / `bytea` |
| `RepeatableControl` | a list of row maps, as `mapRelatedList` returns them | — |
| `SectionControl` | nothing under its own name — its fields go under `section/field` paths | — |
| `ButtonControl` | nothing | — |

Values are not converted: a `numeric` column under a `DoubleControl` fails.

## Dependencies

Control visibility and requirements based on other control values:

```kotlin
// Show "publisher" only when status is "Published"
"publisher" to StringControl(
    label = "Publisher",
    dependencies = mapOf(
        "visibility" to ControlDependency(
            controlPath = "status",
            value = BookStatus.Published,
            dependencyType = DependencyType.Visible,
            comparisonType = ComparisonType.Equals
        )
    )
)

// Make "isbn" required only when "has_isbn" is checked
"isbn" to StringControl(
    label = "ISBN",
    dependencies = mapOf(
        "required" to ControlDependency(
            controlPath = "has_isbn",
            value = true,
            dependencyType = DependencyType.Required,
            comparisonType = ComparisonType.Equals
        )
    )
)
```

`comparisonType` also accepts `ComparisonType.OneOf`, matching against a `Collection` passed as `value`, and
`ComparisonType.NotEquals`. A hidden control's `currentValue` is forced to `null` before it reaches
`FormResultData`, so conditionally-shown fields never leak stale data into a save. A dependency whose path
doesn't resolve to a control throws — a typo there would otherwise silently count as met.

## Control Actions and Path Resolution

Every control carries an optional list of `ControlAction<T>`, run on value change — for `ButtonControl` that
means on click — or, with `executeOnInit = true`, once when the control is first composed (on form load, or when
a `RepeatableControl` adds a row). The action lambda runs with `ActionContext<T>` as receiver, giving access to
`sourceValue`, the triggering control's `ControlContext`, and helpers to reach across the form:

```kotlin
"country" to EnumControl(
    label = "Country",
    enumClass = Country::class,
    actions = listOf(ControlAction { updateControl("../city", null) }) // clear a sibling when country changes
)
```

- `updateControl(path, value)` / `updateControls(path, value)` — write one or many control values by path; a value
  the control does not accept (`Control.acceptsValue`) throws `IllegalArgumentException` naming both paths, and
  sections, repeatable lists and buttons accept none
- `updateLabel(path, label)` — override a control's rendered label

Paths are resolved relative to the acting control's container: `./name` addresses a sibling, a leading `/`
starts from the root (`/basic_info/status`), and a bare `name` is treated as absolute.
Both containers nest state paths: a `SectionControl` appends its name (`basic_info/name`), a `RepeatableControl`
appends its name and the row (`authors[rowId]/name`), and `../name` steps out one container. `updateControls`
additionally accepts a `*` wildcard segment (e.g. `"rows/*/total"`) and updates every matching control in
`FormState`. A path that doesn't resolve to a control throws; only a `*` pattern may match nothing, since a
list can have no rows.

Actions run in a supervised scope (`rememberSupervisedCoroutineScope()` from `ui-core`), which is also the
`coroutineScope` in `ActionContext`. An exception thrown by an action, or by a coroutine it launches there, is
logged and shown in the global error dialog instead of taking the window down. That is a safety net for bugs:
expected failures should still be reported through `DataResult`, a dialog and `false`.

## Sections

A `SectionControl` groups controls in a titled card and owns them, the same way a `RepeatableControl` owns its
row controls:

```kotlin
"play_time_section" to SectionControl(
    label = "Play time",
    columns = 2,
    controls = mapOf(
        "play_time_hours" to DoubleControl(label = "Hours"),
        "completion_count" to IntegerControl(label = "Completions")
    ),
    dependencies = mapOf("visible" to ControlDependency("/basic_info/status", GameStatus.Played, DependencyType.Visible, ComparisonType.Equals))
)
```

A section is one data level — a repeatable row without the `[rowId]` — so everything under it is addressed by
path: `getCurrent("play_time_section/play_time_hours")` in a save action, `setFieldErrors("basic_info/name", ...)`
in a validator, `map("play_time_section/play_time_hours")` in `loadData` (the column defaults to the last
segment), and `"/basic_info/status"` in a dependency reaching another section. Initial values come keyed by those
paths too, which keeps `defaults + loaded + payload` a plain map `+` — a section passed as a nested map
(`"basic_info" to mapOf(...)`) would be replaced whole by the next map, so it's rejected.

Hiding a section hides its children too: they skip validation and `getCurrent` returns `null` for them, while
`getInitial` still returns what was loaded. A section works inside a repeatable row as well, where its fields
land in that row (`rowData.getCurrent("details/name")`).

## Repeatable Sections

Dynamic rows for collections like order items or authors:

```kotlin
"authors" to RepeatableControl(
    label = "Authors",
    rowControls = mapOf(
        "name" to StringControl(label = "Name", required = true),
        "role" to EnumControl(label = "Role", enumClass = AuthorRole::class)
    ),
    rowOrder = listOf("name", "role"),
    validationOptions = RepeatableValidation(minItems = 1, maxItems = 10, uniqueFields = listOf("name"))
)
```

`uniqueFields` rejects rows whose combination of listed fields duplicates another row. A `RepeatableControl`
doesn't just hand back a flat list — its `currentValue` is a `RepeatableResultValue` with the rows already sorted
into `addedRows`, `modifiedRows`, `deletedRows` and `allCurrentRows`, so a `save` action can insert/update/delete
each bucket directly instead of diffing the list itself.

## Validation

Validation runs in three stages, in order, each gating the next:

1. **Field validation** — built into each control's `ControlValidator` (requiredness, format, dependency
   requirements). Failures go to `ErrorManager` as field or format errors and stop the action.
2. **Business rules** — `FormValidator.validateBusinessRules(FormResultData): Boolean`, overridden per form for
   checks that span multiple fields.
3. **Action-specific validation** — `FormValidator.defineActionValidations()`, keyed by the same `actionKey`
   passed to `triggerAction`. Runs *after* the first two and always runs for that action, regardless of the
   button's `validates` flag — the natural place for a uniqueness check that only matters on `save`, not on
   `delete`.

```kotlin
class GameCategoryValidator : FormValidator() {

    override fun defineActionValidations(): Map<String, (FormResultData) -> Boolean> = mapOf(
        "save" to { result -> checkIfUnique(result) }
    )

    private fun checkIfUnique(result: FormResultData): Boolean {
        val name = result.getCurrentAs<String>("name")
        val id = result.getCurrent("id")
        val params = mutableMapOf<String, Any?>("name" to name)

        val builder = db.select("COUNT(*)").from("games.categories")
        if (id != null) {
            builder.where("id != @id AND name = @name")
            params["id"] = id
        } else {
            builder.where("name = @name")
        }

        return when (val count = builder.asResult().fetchField<Long>(params)) {
            is DataResult.Success -> if (count.value > 0) {
                errorManager.setFieldErrors("name", listOf("Category already exists"))
                false
            } else true
            is DataResult.Failure -> false
        }
    }
}
```

`FormValidator` is a `KoinComponent` with `db` and `errorManager` available directly, so business-rule
and action validators can query the database and attach errors to specific fields in one place.

## Localization

The engine owns its strings. `src/commonMain/resources/i18n/{en,pl}.json` are compiled by the
[Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n) plugin into
`io.github.octaviusframework.form.localization.FormTr`, generated from this module alone:

```kotlin
octaviusI18n {
    generators {
        create("form") {
            targetPackage = "io.github.octaviusframework.form.localization"
            objectName = "FormTr"
        }
    }
}
```

Everything the engine renders or reports on its own — validation messages, dropdown placeholders, repeatable
row labels, the error summary — resolves through `FormTr`, never through the host application's translation
object. Generic strings shared with the rest of the UI (`Tr.Action.add()`, `Tr.Expandable.expand()`,
`Tr.Search.*`) are not repeated here — they come from `ui-core`'s `Tr`, which the engine depends on anyway.
The active language is global —
`OctaviusI18n.currentLanguage` — and shared with every other translation object in the application.

## Architecture

```
form-engine/
├── component/
│   ├── FormSchema.kt          # FormSchema + FormSchemaBuilder (template method)
│   ├── FormState.kt           # Reactive per-control state, keyed by hierarchical path; FormLoadState
│   ├── FormHandler.kt         # Orchestrates load → validate → action
│   ├── InitDataException.kt   # initData that does not fit the schema (unknown key, value the control rejects)
│   ├── FormResultException.kt # reading FormResultData failed (no control at the path, value of another type)
│   ├── FormActionTrigger.kt   # triggerAction(actionKey, validates): Boolean — the whole outward contract
│   ├── FormView.kt            # Composable rendering content + action bar
│   ├── FormDataManager.kt     # initData() / definedFormActions() contract
│   ├── FormLoader.kt          # loadData DSL: from/map/mapOneToOne/mapRelatedList
│   ├── FormValidator.kt       # Field / business-rule / action validation contract
│   ├── ErrorManager.kt        # Global, field and format error state
│   └── PathResolver.kt        # Relative ("./", "../") and wildcard ("*") path resolution
│
├── control/
│   ├── base/
│   │   ├── Control.kt         # Base control class: lifecycle, actions, rendering
│   │   ├── ControlData.kt     # ControlState, ControlContext, FormResultData helpers
│   │   ├── Dependencies.kt    # ControlDependency, DependencyType, ComparisonType
│   │   ├── ControlAction.kt   # ControlAction, ActionContext (updateControl/updateLabel)
│   │   └── ValidationOptions.kt
│   ├── type/                  # primitive/, number/, datetime/, selection/, collection/,
│   │                           # container/, repeatable/, button/ — one package per category
│   ├── validator/              # One ControlValidator implementation per control type
│   └── layout/                 # Section and repeatable-row rendering, shared layout helpers
│
└── resources/i18n/            # en/pl translations, generated into `FormTr`
```

The module targets the desktop JVM only and depends on `ui-core` for theme, dialogs and snackbars, on
[Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql) for `OctaviusClient`,
and on [Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n) for its translations.
`FormValidator` and `FormDataManager` obtain `OctaviusClient` through Koin.

It does **not** depend on the application's navigation module, and deliberately so: nothing here knows what a
screen or a router is. `FormView` renders, `triggerAction` reports success, and every decision that follows
belongs to the caller.

Terminals throw by default there; the engine asks for the result style with `.asResult()`, so a failure the
database reports arrives as a `DataResult.Failure` and reaches the user through `GlobalDialogManager` rather
than unwinding the form.
