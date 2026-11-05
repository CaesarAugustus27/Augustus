rootProject.name = "Augustus"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("composeLibs") {
            from(files("ui-core/gradle/libs.versions.toml"))
        }
    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }
}

include(
    ":desktop-app",
    ":form-engine",
    ":report-engine",
    ":api-server",
    ":ui-core",
    ":navigation",
    ":shared-contract",
    ":feature-contract",
    // Browser extension
    ":browser-extension",
    ":browser-extension:content-script",
    ":browser-extension:background",
    ":browser-extension:popup",
    ":browser-extension:chrome-api",
)

// Featury dołączają się same: każdy katalog feature-* z własnym build.gradle.kts (poza kontraktem) jest
// modułem, a desktop-app zależy od wszystkich (patrz jego build.gradle.kts). Katalog, którego nie ma na
// danej gałęzi albo który git ignoruje, po prostu nie trafia do builda - nie ma listy do poprawiania.
rootDir.listFiles()!!
    .filter { it.name.startsWith("feature-") && it.name != "feature-contract" }
    .filter { it.resolve("build.gradle.kts").isFile }
    .sortedBy { it.name }
    .forEach { include(":${it.name}") }