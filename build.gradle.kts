import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.dokka)
}

// Konfiguracja Dokka - agregacja dokumentacji z wielu modułów
dokka {
    moduleName.set("Augustus")

    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

// Zależności Dokka - agregacja dokumentacji ze wszystkich modułów
dependencies {
    dokka(projects.apiServer)
    dokka(projects.sharedContract)
    dokka(projects.uiCore)
    dokka(projects.desktopApp)
    dokka(projects.formEngine)
    dokka(projects.reportEngine)
    dokka(projects.featureAsianMedia)
    dokka(projects.featureGames)
    dokka(projects.featureContract)
    dokka(projects.browserExtension.popup)
    dokka(projects.browserExtension.contentScript)
    dokka(projects.browserExtension.background)
    dokka(projects.browserExtension.chromeApi)
}

// Aplikujemy plugin Dokka do wszystkich subprojektów z kodem Kotlin
subprojects {
    val excludedModules = setOf("browser-extension", "buildSrc")
    if (name !in excludedModules) {
        apply(plugin = "org.jetbrains.dokka")

        // Konfigurujemy Dokkę dla KAŻDEGO subprojektu
        extensions.configure<DokkaExtension> {
            // Ustawiamy unikalną nazwę modułu na podstawie pełnej ścieżki projektu
            // np. :database:api -> database-api, :core -> core
            moduleName.set(path.removePrefix(":").replace(":", "-"))

            dokkaSourceSets.configureEach {
                documentedVisibilities.set(
                    setOf(
                        VisibilityModifier.Public,
                        VisibilityModifier.Private,
                        VisibilityModifier.Protected,
                        VisibilityModifier.Internal
                    )
                )
                skipEmptyPackages.set(true)
            }
        }
    }
}
