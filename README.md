# Augustus

<div align="center">

**A modular desktop application for managing media collections, built with custom-engineered frameworks**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18+-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Octavius for PostgreSQL](https://img.shields.io/maven-central/v/io.github.octavius-framework/driver.svg?label=Octavius%20for%20PostgreSQL&color=orange)](https://github.com/Octavius-Framework/octavius-postgresql)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/)
</div>

---

## Overview

Augustus is a Kotlin Multiplatform desktop application for tracking manga, light novels, and game collections. What makes it unique is that it's built entirely on **custom-engineered frameworks** — a form engine, report engine, and database access layer — designed from scratch to solve real problems without the overhead of traditional solutions.

## Highlights

| Component                                                                                | Description                                                                                                                                   |
|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| **[Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql)** | *External Library.* A wire-protocol driver, a data access client over it, and a migrator — SQL-first, with type mapping read from the catalog |
| **[Form Engine](form-engine/)**                                                          | Declarative form builder: dependencies, cross-control actions, repeatable sections, three-level validation                                    |
| **[Report Engine](report-engine/)**                                                      | Turns a SQL query into an interactive table — filters that write SQL, multi-column sorting, column management, saved layouts                  |
| **Browser Extension**                                                                    | Kotlin/JS Chrome extension for importing data from external sources                                                                           |

## Tech Stack

<table>
<tr>
<td>

**Core**
- Kotlin Multiplatform
- Compose Multiplatform
- PostgreSQL 18+
- Material 3

</td>
<td>

**Backend / Data**
- Octavius for PostgreSQL (custom wire-protocol driver + client)
- HikariCP
- Ktor (API server)
- kotlinx-serialization

</td>
</tr>
</table>

## Architecture

The project follows a modular distributed architecture. The Core Database layer is developed as a separate library.

```
Augustus/
├── desktop-app/             # Main application entry point
│
├── form-engine/             # Declarative form framework
├── report-engine/           # Dynamic table framework
├── ui-core/                 # Shared UI components, theme, utilities
├── navigation/              # Tab/screen stack router — app side, engines do not depend on it
│
├── feature-asian-media/     # Manga, novels, manhwa tracking
├── feature-books/           # Books and literature tracking
├── feature-games/           # Game collection management
├── feature-sandbox/         # Sandbox testing environment
├── feature-contract/        # Shared feature interfaces and models
│
├── api-server/              # REST API for browser extension
├── shared-contract/         # Contracts shared between the app and the extension
└── browser-extension/       # Kotlin/JS Chrome extension
```

*Note: The `database` module is externalized.*

## Custom Frameworks

### Form Engine

Turns a schema — controls plus a display order — into a full CRUD form:

- **Rich control types**: primitives, dropdowns (enum/database), dates/intervals, files, sections, repeatable rows
- **Dependencies**: show/hide or require a control based on another control's value
- **Cross-control actions**: a control action can read or write any other control by relative or wildcard path
- **Three-level validation**: per-field, business rules, and per-action, each with its own error surface
- **Diffed repeatable rows**: added/modified/deleted rows separated for you, not just a flat list

[Learn more →](form-engine/)

### Report Engine

Turns a SQL query into an interactive table — the base query is never rewritten, just wrapped in a subquery:

- **Filters that write SQL**: each column type builds its own parameterised query fragment, with explicit NULL semantics
- **Multi-column sorting**: ordered, drag-reorderable sort chips
- **Column management**: visibility and order, adjusted at runtime
- **Saved layouts**: filters, sorting, visibility and page size persisted per report in the database
- **Server-side pagination**: `COUNT` + `LIMIT`/`OFFSET`, never loads the full result set

[Learn more →](report-engine/)

### Database Layer (Octavius for PostgreSQL)

SQL-first approach with automatic mapping (imported as a library):

```kotlin
// Your query shapes the result — not the ORM
val books = db.select("id", "title", "author", "status")
    .from("books")
    .where("status = @status")
    .orderBy("title")
    .fetchObjects<Book>("status" to BookStatus.Reading)
```

The schema is kept up to date by the same library's migrator — `OctaviusMigrator(dataSource).migrate()` at
startup, over the `V<version>__<description>.sql` files each module ships in its own resources.

[See Repository →](https://github.com/Octavius-Framework/octavius-postgresql)

## Getting Started

### Requirements

- JDK 25+
- PostgreSQL 18+ — the driver speaks Wire Protocol v3.2, which no earlier server does
- Database `augustus` created locally

### Run

```bash
./gradlew run
```

### Build Browser Extension

```bash
./gradlew assembleBrowserExtension
# Output: browser-extension/build/extension/
```

## Localization

Translations are managed using the [`octavius-i18n`](https://github.com/Octavius-Framework/octavius-i18n) Gradle plugin. Check its repository for detailed instructions on adding and configuring translations.