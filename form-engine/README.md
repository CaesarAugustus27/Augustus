# Form Engine

<div align="center">

**Forms for Compose Multiplatform, built from a schema of controls**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/form-engine/)

</div>

---

## Overview

A form is a map of named controls plus two lists that set their order on screen. The engine renders the controls,
holds their state, loads initial data through a mapping DSL, runs three stages of validation, and lets one control's
value change another control's visibility, requirement or content.

Saving is ordinary `OctaviusClient` code. Every form action receives a `FormResultData` with the current and initial
value of each control.

## Control types

| Category   | Package                   | Controls                                                                                           |
|------------|---------------------------|----------------------------------------------------------------------------------------------------|
| Primitive  | `control.type.primitive`  | `StringControl`, `MultilineStringControl`, `CheckboxControl`, `SwitchControl`, `FilePickerControl` |
| Number     | `control.type.number`     | `IntegerControl`, `DoubleControl`, `BigDecimalControl`                                             |
| Date, time | `control.type.datetime`   | `DateTimeControl`, `IntervalControl`                                                               |
| Selection  | `control.type.selection`  | `EnumControl`, `DatabaseControl`, `RadioGroupControl`, `CheckboxGroupControl`                      |
| Collection | `control.type.collection` | `StringListControl`                                                                                |
| Container  | `control.type.container`  | `SectionControl`                                                                                   |
| Repeatable | `control.type.repeatable` | `RepeatableControl`                                                                                |
| Button     | `control.type.button`     | `ButtonControl`                                                                                    |

`DateTimeControl<T>` works through a `DateTimeAdapter<T>`. The functions `DateControl`, `LocalDateTimeControl`,
`LocalTimeControl` and `InstantControl` create it for the usual types.

`EnumControl` takes a `KClass<T>` whose `T` implements `EnumWithFormatter`. `DatabaseControl` takes a query and wraps
it in a subquery to add search, sorting and paging:

```kotlin
"author_id" to DatabaseControl(
    label = "Author",
    query = QueryFragment("SELECT id, name FROM books.authors"),
    displayColumn = "name"
)
```

Its value is the `idColumn` of the selected row (`id` by default, always an `Int`). Selecting an item passes the whole
row to `ActionContext.payload`, so an action can fill other controls from it. `orderBy`, `searchFilter` and `pageSize`
are optional.

## Quick start

### 1. Define the schema

```kotlin
class BookFormSchemaBuilder : FormSchemaBuilder() {

    override fun defineControls(): Map<String, Control<*>> = mapOf(
        "id" to IntegerControl(label = null),
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
            actions = listOf(ControlAction { AppRouter.goBack() })
        )
    )

    override fun defineContentOrder() = listOf("title", "author", "year", "status")

    override fun defineActionBarOrder() = listOf("cancel_button", "save_button")
}
```

A control missing from both order lists is not rendered but still holds a value, like `id` here.

### 2. Implement the data manager

```kotlin
class BookFormDataManager : FormDataManager() {

    override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
        return loadData(payload["id"]) {
            from("books", "b")
            map("id")
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
                GlobalDialogManager.show(ErrorDialogConfig(result.error))
                false
            }
        }
    }
}
```

An action reports failure by returning `false` and shows the error itself.

`getCurrent` and `getInitial` read a value by path. `getCurrentAs<T>` and `getInitialAs<T>` also cast it. All four throw
`FormResultException` with the path when no control lies at the path or the path goes through a control that is not a
section. The `As` variants also throw when the value is not a `T`, which includes `null` for a non-null `T`, as with a
hidden control.

### 3. Create the screen

`FormView(handler)` is a composable. In Augustus it goes into a `ComponentScreen` from the `navigation` module:

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
            return ComponentScreen("Books") { FormView(formHandler) }
        }
    }
}

AppRouter.navigateTo(BookFormScreen.create(bookId))
```

`FormHandler` loads the initial data in the background. `FormView` shows a spinner until the data is ready and an error
message if loading failed (`loadState`). While an action runs, an overlay blocks input (`actionTriggered`).

`triggerAction(actionKey, validates)` returns `true` when the action ran and succeeded, and `false` when validation
failed, the key is unknown or the action failed. It runs in the handler's scope, so a save still finishes if the screen
closes in the meantime.

## Loading data

`loadData` maps controls to columns and runs one query:

```kotlin
override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
    return loadData(payload["id"]) {
        from("books", "b")
        map("title")
        map("publication_year", "pub_year") // the column has another name than the control

        mapOneToOne {
            from("book_details", "bd")
            on("bd.book_id = b.id")
            map("isbn")
            existenceFlag("has_details", "bd.id") // true when the related row exists
        }

        mapRelatedList("authors") {
            from("book_authors", "ba")
            linkedBy("ba.book_id")
            orderBy("ba.position")
            map("name")
            map("role")
        }
    }
}
```

- `map` takes a control path and a column. The column defaults to the last path segment in snake_case.
- `mapOneToOne` becomes a `LEFT JOIN`.
- `mapRelatedList` becomes an `ARRAY(SELECT ROW(...))::record[]` subquery, read back as a list of maps for a
  `RepeatableControl`. `linkedBy` compares the column with the `id` given to `loadData`, or with its second argument.
  Rows come back in `orderBy` order, and in no particular order without it.
- `id` is the primary key of the main table. `idColumn("uuid")` names another column.

With a `null` id, `loadData` skips the query and returns an empty map, which is what a new record needs. If the query
fails or no row matches, it shows a dialog and returns `null`. `initData` passes that on, and the form ends in
`FormLoadState.Failed`:

```kotlin
override fun initData(payload: Map<String, Any?>): Map<String, Any?>? {
    val loaded = loadBookData(payload["id"]) ?: return null
    return defaults + loaded + payload
}
```

### Initial data must fit the schema

`initData` returns a flat map keyed by control path. Every key must name a control, and every non-null value must be one
the control accepts (`Control.acceptsValue`). Otherwise loading throws `InitDataException` with the schema path of the
value, e.g. `"basic_info/status"`, or `"publications/status"` for a field of repeatable rows. The exception ends in the
error dialog and `FormLoadState.Failed`.

| Control                                   | Accepts                                                               | Typical PostgreSQL column                                                        |
|-------------------------------------------|-----------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `StringControl`, `MultilineStringControl` | `String`                                                              | `text`, `varchar`                                                                |
| `IntegerControl`                          | `Int`                                                                 | `integer` (`smallint` reads as `Short` and `bigint` as `Long`, and neither fits) |
| `DoubleControl`                           | `Double`                                                              | `double precision` (`real` reads as `Float`)                                     |
| `BigDecimalControl`                       | `BigDecimal`                                                          | `numeric`                                                                        |
| `CheckboxControl`, `SwitchControl`        | `Boolean`                                                             | `boolean`                                                                        |
| `DateTimeControl<T>`                      | the adapter's `valueClass`                                            | `date`, `time`, `timestamp`, `timestamptz`                                       |
| `IntervalControl`                         | `kotlin.time.Duration`                                                | `interval` (through the application's converter)                                 |
| `EnumControl<T>`                          | a constant of `T`                                                     | the enum type registered with `@PgEnumType`                                      |
| `DatabaseControl`                         | `Int`                                                                 | the `integer` key of the related row                                             |
| `RadioGroupControl<T>`                    | one of its option values                                              |                                                                                  |
| `CheckboxGroupControl<T>`                 | a list of its option values                                           |                                                                                  |
| `StringListControl`                       | `List<String>`                                                        | `text[]`                                                                         |
| `FilePickerControl<T>`                    | `String` (`PATH_STRING`) or `ByteArray` (`CONTENT_BYTES`)             | `text`, `bytea`                                                                  |
| `RepeatableControl`                       | a list of row maps, as `mapRelatedList` returns them                  |                                                                                  |
| `SectionControl`                          | nothing under its own name, its fields go under `section/field` paths |                                                                                  |
| `ButtonControl`                           | nothing                                                               |                                                                                  |

Values are not converted, so a `numeric` column under a `DoubleControl` fails.

## Dependencies

A dependency shows a control or makes it required based on another control's value:

```kotlin
"publisher" to StringControl(
    label = "Publisher",
    dependencies = mapOf(
        "visible" to ControlDependency(
            controlPath = "status",
            value = BookStatus.Published,
            dependencyType = DependencyType.Visible,
            comparisonType = ComparisonType.Equals
        )
    )
)

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

- The engine does not read the map keys, `dependencyType` alone decides what a dependency does. Each dependency
  still needs its own key, because `mapOf` keeps only the last value under a repeated key.
- `comparisonType` is `Equals`, `NotEquals` or `OneOf`, which takes a `List` as `value`.
- A control shows when all its `Visible` dependencies are met, and is required when any `Required` one is.
- A hidden control has `currentValue = null` in `FormResultData` and skips validation. `getInitial` still returns the
  loaded value.
- A dependency path that matches no control throws `IllegalArgumentException`.

## Actions and paths

Each control takes a list of `ControlAction<T>`. An action runs when the value changes, or on click for a
`ButtonControl`. With `executeOnInit = true` it also runs when the control is first composed: on form load, or when a
row is added to a `RepeatableControl`.

```kotlin
"country" to EnumControl(
    label = "Country",
    enumClass = Country::class,
    actions = listOf(ControlAction { updateControl("./city", null) })
)
```

The lambda runs with `ActionContext<T>` as its receiver:

| Member                           | Use                                                                       |
|----------------------------------|---------------------------------------------------------------------------|
| `sourceValue`                    | new value of the control                                                  |
| `payload`                        | extra data from the control, e.g. the selected row of a `DatabaseControl` |
| `trigger`                        | runs a form action with `triggerAction`                                   |
| `errorManager`                   | sets field and global errors                                              |
| `coroutineScope`                 | scope for asynchronous work                                               |
| `readControl<V>(path)`           | reads another control's value                                             |
| `updateControl(path, value)`     | writes another control's value                                            |
| `updateControls(pattern, value)` | writes every matching control                                             |
| `updateLabel(path, label)`       | replaces a control's label                                                |

A `SectionControl` adds `name/` to the paths of its children and a `RepeatableControl` adds `name[rowId]/`. Paths are
resolved against that structure:

| Path                    | Points at                                   |
|-------------------------|---------------------------------------------|
| `name`, `/section/name` | a control counted from the form root        |
| `./name`                | a sibling in the same container             |
| `../name`               | a control one container up                  |
| `rows[*]/total`         | `total` in every row, `updateControls` only |

A path that matches no control throws `IllegalArgumentException`. Only a pattern with `*` may match nothing, since a list
can have no rows. A value the control does not accept also throws, under the same `acceptsValue` check as initial data.
Sections, repeatable lists and buttons accept no value this way.

An exception from an action, or from a coroutine it launches in `coroutineScope`, ends in the global error dialog and the
window stays open. Report expected failures through `DataResult`, your own dialog and `false`.

## Sections

A `SectionControl` groups controls in a titled card. `columns` sets the layout, `collapsible` and `initiallyExpanded` set
the folding:

```kotlin
"play_time_section" to SectionControl(
    label = "Play time",
    columns = 2,
    controls = mapOf(
        "play_time_hours" to DoubleControl(label = "Hours"),
        "completion_count" to IntegerControl(label = "Completions")
    ),
    dependencies = mapOf(
        "visible" to ControlDependency("/basic_info/status", GameStatus.Played, DependencyType.Visible, ComparisonType.Equals)
    )
)
```

Fields of a section are addressed by path everywhere:

| Where                           | Example                                                    |
|---------------------------------|------------------------------------------------------------|
| Reading a result                | `getCurrent("play_time_section/play_time_hours")`          |
| Field errors                    | `setFieldErrors("play_time_section/play_time_hours", ...)` |
| `loadData`                      | `map("play_time_section/play_time_hours")`                 |
| `initData`                      | `"play_time_section/play_time_hours" to 12.5`              |
| A dependency in another section | `"/play_time_section/play_time_hours"`                     |

A map under the section's own name throws `InitDataException`.

Hiding a section hides its fields. They skip validation and `getCurrent` returns `null` for them, while `getInitial`
still returns what was loaded.

## Repeatable rows

`RepeatableControl` holds a list of rows, each with its own set of controls:

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

`minItems` also adds empty rows until the count is met. `uniqueFields` rejects a row whose combination of the listed
fields repeats in another row.

The control's current value is a `RepeatableResultValue` with the rows sorted into `addedRows`, `modifiedRows`,
`deletedRows` and `allCurrentRows`, each a list of `FormResultData`:

```kotlin
val authors = formData.getCurrentAs<RepeatableResultValue>("authors")
val removedIds = authors.deletedRows.map { it.getInitialAs<Int>("author_id") }
val addedIds = authors.addedRows.map { it.getCurrentAs<Int>("author_id") }
```

A row can also contain a `SectionControl`. Its fields are then addressed by path within the row, as in the form: the
row's map in `initData` uses `"details/name"`, and a row of the result is read with `row.getCurrent("details/name")`.

## Validation

Validation runs in three stages, in this order. A failed stage shows a snackbar and the action does not run.

1. **Fields.** Each control checks `required`, its format and its `validationOptions`, e.g.
   `StringValidation(maxLength = 100)`. Runs only with `validates = true`.
2. **Business rules.** `FormValidator.validateBusinessRules(FormResultData)`, for checks that span several fields. Runs
   only with `validates = true`.
3. **Action.** `FormValidator.defineActionValidations()`, keyed by the `actionKey` of `triggerAction`. Always runs for
   that action, so it fits a check that matters on `save` and not on `delete`.

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
            is DataResult.Failure -> {
                GlobalDialogManager.show(ErrorDialogConfig(count.error))
                false
            }
        }
    }
}
```

`FormValidator` provides `db`, `errorManager`, `formState` and `formSchema`. Field and format errors from `ErrorManager`
appear under the control, and global errors in a card above the form.

## Localization

The engine's strings are in `src/commonMain/resources/i18n/{en,pl}.json`, which
[Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n) compiles into
`io.github.octaviusframework.form.localization.FormTr`. Strings shared with the rest of the UI come from `Tr` in
`ui-core`.

## Source layout

```
form-engine/
├── component/
│   ├── FormSchema.kt          # FormSchema, FormSchemaBuilder
│   ├── FormState.kt           # Per-control state keyed by path, FormLoadState
│   ├── FormHandler.kt         # Loads data, validates, runs actions
│   ├── FormActionTrigger.kt   # triggerAction
│   ├── FormView.kt            # Composable with the content and the action bar
│   ├── FormDataManager.kt     # initData, definedFormActions
│   ├── FormLoader.kt          # loadData DSL
│   ├── FormValidator.kt       # Business-rule and action validation
│   ├── ErrorManager.kt        # Global, field and format errors
│   ├── PathResolver.kt        # Relative paths and wildcards
│   ├── InitDataException.kt   # initData does not fit the schema
│   └── FormResultException.kt # A value cannot be read from FormResultData
│
├── control/
│   ├── base/                  # Control, ControlAction, ControlData, Dependencies, ValidationOptions
│   ├── type/                  # One package per control category
│   ├── validator/             # ControlValidator implementations
│   └── layout/                # Rendering of sections, repeatable rows and labels
│
└── resources/i18n/            # en and pl translations
```

The module targets the desktop JVM. It depends on `ui-core` for the theme, dialogs and snackbars, on
[Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql) for `OctaviusClient`, which it gets
through Koin, and on Octavius I18n for translations.

The tests need no database:

```bash
./gradlew :form-engine:desktopTest
```
