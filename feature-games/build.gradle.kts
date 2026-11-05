import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")
    // Dla wtyczki: DTO API i enumy we wspólnym kodzie, odczyt stron SteamDB w jsMain. Korzysta z nich
    // content script, więc Compose jest wyłącznie w desktopMain.
    js {
        browser()
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(projects.sharedContract)
            implementation(libs.octavius.i18n.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.octavius.pg.model)
        }

        desktopMain.dependencies {
            implementation(projects.uiCore)
            implementation(projects.navigation)
            implementation(composeLibs.components.resources)
            implementation(libs.kotlinx.coroutines.core)

            implementation(projects.formEngine)
            implementation(projects.reportEngine)
            implementation(projects.featureContract)

            implementation(project.dependencies.platform(libs.koin.bom))

            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            implementation(libs.ktor.server.core)
        }
    }
}

// Compose jest tylko w desktopMain, a kompilator nie przyjmuje targetu bez Compose Runtime
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.jvm))
}

octaviusI18n {
    generators {
        create("games") {
            targetPackage = "org.octavius.modules.games.localization"
            objectName = "GamesTr"
        }
    }
}
