# Augustus

<div align="center">

**A desktop catalogue of manga, novels, games and books, with a browser extension that feeds it**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18+-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Octavius for PostgreSQL](https://img.shields.io/maven-central/v/io.github.octavius-framework/driver.svg?label=Octavius%20for%20PostgreSQL&color=orange)](https://github.com/Octavius-Framework/octavius-postgresql)
[![KDoc](https://img.shields.io/badge/KDoc-Documentation-blue)](https://caesaraugustus27.github.io/Augustus/)
</div>

---

## Overview

Augustus is a Kotlin Multiplatform desktop application that keeps a catalogue in a local PostgreSQL database. Each kind
of media is a feature module with its own tab, database schema, forms and reports:

| Module                | Contents                                                        |
|-----------------------|-----------------------------------------------------------------|
| `feature-asian-media` | Manga, manhwa, manhua and novels                                |
| `feature-games`       | Games, with series, categories, play time and ratings           |
| `feature-books`       | Books and authors                                               |
| `feature-sandbox`     | Every form control and report column, with no tables of its own |

A [browser extension](#browser-extension) adds series from NovelUpdates and MangaUpdates, and repeats in the catalogue
what you do on NovelUpdates and SteamDB.

The application is built on these parts:

| Part                                                                                 | What it does                                                      |
|--------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| [Form Engine](form-engine/)                                                          | Forms from a schema of controls                                   |
| [Report Engine](report-engine/)                                                      | Tables with filters, sorting and saved layouts from a SQL query   |
| [Octavius for PostgreSQL](https://github.com/Octavius-Framework/octavius-postgresql) | External library: wire-protocol driver, SQL client and migrator   |
| [Octavius I18n](https://github.com/Octavius-Framework/octavius-i18n)                 | External Gradle plugin: typed translation objects from JSON files |

Also used: Compose Multiplatform with Material 3, HikariCP, Koin, Ktor and kotlinx-serialization.

## Getting started

You need:

- JDK 25
- PostgreSQL 18 or newer, because the driver uses wire protocol 3.2 and older servers reject it
- a database named `augustus`, with the `pg_trgm` extension available, because a migration creates it

```bash
./gradlew :desktop-app:run
```

The connection is configured in `%APPDATA%\Augustus\settings.json`, or in the home directory on systems without
`APPDATA`. By default it uses `jdbc:octavius://localhost:5432/augustus` and the user `postgres`.

Any failure while connecting or migrating opens the same window with connection settings and a retry button. The cause is
in the console and in `desktop-app/logs/octavius.log`.

Other commands:

```bash
./gradlew :form-engine:desktopTest # tests
./gradlew dokkaGenerateHtml        # KDoc, into build/dokka/html
```

## Project layout

```
Augustus/
├── desktop-app/             # Entry point: settings, migrations, main window
│
├── form-engine/             # Forms
├── report-engine/           # Tables
├── ui-core/                 # Theme, dialogs, snackbars, shared components
├── navigation/              # Tabs and screen stacks
│
├── feature-asian-media/
├── feature-books/
├── feature-games/
├── feature-sandbox/
├── feature-contract/        # FeatureModule and ApiModule interfaces
│
├── api-server/              # Embedded Ktor server for the extension
├── shared-contract/         # Code shared by the application and the extension
└── browser-extension/
```

### Features

At startup `desktop-app` scans the classpath for `object`s under `org.octavius` that implement `FeatureModule`. Each one
supplies a tab, API routes, screens that can be opened from outside, and a database schema. `settings.gradle.kts`
includes every `feature-*` directory that has a `build.gradle.kts`, and `desktop-app` depends on all of them, so a new
feature is a new directory and nothing else changes.

### Database

SQL is written by hand with the Octavius client. Each feature with tables has its own schema, migration sequence and
history table. Migrations are SQL files in `src/desktopMain/resources/db/migration/<schema>/`, named
`V<yyyy.MM.dd.HH.mm>__<description>.sql`, and they run at startup: `public` first, then the feature schemas in feature
order.

### Translations

Each module has `i18n/{en,pl}.json` in its resources and a generated typed object: `BooksTr.Form.titlePl()` reads the key
`form.titlePl`. The active language is global and comes from the application settings.

## Browser extension

`browser-extension/` is a Manifest V3 Chrome extension written in Kotlin/JS. It reads pages on the sites below and talks to
the running application at `http://localhost:8080`. The application does all the writing to the database. The extension
sends nothing to the sites it reads.

| Site                       | What you do                           | What happens in Augustus                                  |
|----------------------------|---------------------------------------|-----------------------------------------------------------|
| NovelUpdates, MangaUpdates | Open the popup on a series page       | The series is checked, and can be added, linked or opened |
| NovelUpdates               | Move a series to another reading list | The status of the series changes                          |
| SteamDB                    | Click "Ignore" on a game              | The status of the game becomes Trash                      |

### Install

```bash
./gradlew :browser-extension:assembleBrowserExtension
```

Open `chrome://extensions`, turn on developer mode, choose "Load unpacked" and pick `browser-extension/build/extension/`.
After a rebuild, reload the extension on that page. The application has to be running for changes to reach the database.

### Popup

Clicking the extension's icon on a series page reads the titles, the publication type, the original language and the
site's id of the series. Titles in non-Latin scripts are dropped. The application looks for the series by that id, then by
trigram similarity of the titles, and the popup offers one of these:

| Found                 | Buttons                                |
|-----------------------|----------------------------------------|
| A series with this id | Open in Augustus                       |
| A similar title       | Link to that title, Add as a new title |
| Nothing               | Add to Augustus                        |

Adding and linking store the site's id, so the next check recognises the series, and open the title's form in the
application. Linking also appends the new titles and adds a publication of this type if the title has none. The popup
follows the browser's language.

### NovelUpdates reading lists

The service worker watches requests to `updatelist.php` with `act=move`, so a move counts wherever it was clicked: in the
series finder, on a series page or on a reading list. The content script describes the series from the page, and the
application decides:

1. The series id is known: the status changes.
2. Exactly one title has this name, it has no NovelUpdates id yet, and the match is on its main title or its language
   agrees: the id is linked and the status changes.
3. No title has this name: a new title is created with a publication and the id.
4. Anything else is ambiguous: nothing is saved, and the message lists the candidates.

Names are compared without regard to case. NovelUpdates has only novels, so only light, web and published novel
publications change status, and a title with only a manga gets a new publication.

`novelUpdatesListStatuses` in `AsianMediaApi` maps list ids to statuses, and a list missing from it saves nothing. List ids
are separate for each NovelUpdates account.

### SteamDB ignores

This needs the [SteamDB extension](https://github.com/SteamDatabase/BrowserExtension), whose "Ignore" button appears on
game pages and in the tooltip over games on lists. The content script listens for that extension's reply and acts on a
successful ignore. The application then decides:

1. The appid is known: the status changes.
2. Exactly one game has this name: the appid is linked and the status changes. A game can have several appids.
3. No game has this name: a new game is created with the appid.
4. Several games have this name: nothing is saved, and the message lists the candidates.

Names are compared by letters and digits alone, without regard to case, because Steam adds ™, ® and emoji. These are also
removed from the name of a new game. The status is always Trash, and the game's play time and ratings are deleted.

Ignoring a game in the Steam store itself is not picked up.

### Messages and the queue

Each repeated change shows a message in the corner of the page: green for 3 seconds when it was saved, red for 10 seconds
when it was not.

When the application is not running, the change waits in `chrome.storage.local` and goes out with the next change or the
next start of the service worker. A change the application answers with an error status is reported once and not sent
again. A series or game the content script can't find on the page is reported and never sent.

### Parts

| Module           | Role                                                                                      |
|------------------|-------------------------------------------------------------------------------------------|
| `popup`          | Compose UI of the popup and the HTTP client for the `check`, `add`, `link`, `open` routes |
| `content-script` | Runs on the three sites: parses pages, listens to SteamDB, shows messages                 |
| `background`     | Service worker: watches `updatelist.php`, holds the queue, sends changes                  |
| `chrome-api`     | `external` declarations for the Chrome APIs in use                                        |

`feature-asian-media` and `feature-games` also have a `js` target: `commonMain` holds the DTOs shared with the application
and `jsMain` holds the page parsers. The routes are defined in `AsianMediaApi` and `GamesApi`:

| Route                                          | Called by  | Effect                                       |
|------------------------------------------------|------------|----------------------------------------------|
| `POST /api/asian-media/check`                  | popup      | Looks for a series by id or similar title    |
| `POST /api/asian-media/add`                    | popup      | Adds a title and opens its form              |
| `POST /api/asian-media/link`                   | popup      | Links a series to a title and opens its form |
| `POST /api/asian-media/open`                   | popup      | Opens a title's form                         |
| `POST /api/asian-media/novelupdates/list-move` | background | Repeats a reading list move                  |
| `POST /api/games/steam/ignore`                 | background | Repeats an ignored game                      |
