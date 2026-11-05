import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(projects.uiCore)
            implementation(projects.navigation)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        desktopMain.dependencies {
            implementation(libs.octavius.client)
            implementation(libs.octavius.client.scanner)
            implementation(libs.octavius.migrations)
            implementation(libs.classgraph)
            implementation(libs.hikaricp)
            implementation(projects.featureContract)
            implementation(projects.formEngine)
            implementation(projects.reportEngine)
            // Wszystkie featury, które settings.gradle.kts dołączył do builda. Aplikacja znajduje je
            // w czasie działania (FeatureDiscovery), więc nie importuje żadnego z nazwy.
            rootProject.childProjects.values
                .filter { it.name.startsWith("feature-") && it.name != "feature-contract" }
                .forEach { implementation(project(it.path)) }
            implementation(projects.apiServer)
            implementation(compose.desktop.currentOs)

            implementation(project.dependencies.platform(libs.koin.bom))

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.slf4j)

            // Implementacja loggera
            implementation(libs.logback.classic)
        }
        desktopMain.resources.srcDir(layout.buildDirectory.dir("generated/translations"))
    }
}

compose.desktop {
    application {
        mainClass = "org.octavius.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            includeAllModules = true
            packageName = "org.octavius"
            packageVersion = "1.0.0"
//            windows {
//                iconFile.set(project.file("icon.ico"))
//            }
        }
    }
}

octaviusI18n {
    generators {
        create("app") {
            targetPackage = "org.octavius.app.localization"
            objectName = "AppTr"
        }
    }
}
