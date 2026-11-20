import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // API kopii kosza i polubionych oraz zakładka ze statystykami
    jvm("desktop")
    // Rozszerzenie Spicetify; DTO API dzieli z aplikacją przez commonMain
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "augustus.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.octavius.i18n.core)
        }

        desktopMain.dependencies {
            implementation(projects.featureContract)
            implementation(projects.navigation)
            implementation(projects.uiCore)
            implementation(projects.reportEngine)
            implementation(libs.octavius.client)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }

        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Compose jest tylko w desktopMain, a kompilator nie przyjmuje targetu bez Compose Runtime
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.jvm))
}

// Napisy rozszerzenia i zakładki. Rozszerzenie bierze język z klienta Spotify
octaviusI18n {
    generators {
        create("spotify") {
            targetPackage = "org.octavius.modules.spotify.localization"
            objectName = "SpotifyTr"
        }
    }
}

// augustus.js -> katalog rozszerzeń Spicetify (%APPDATA%\spicetify\Extensions), potem `spicetify apply`, które
// restartuje Spotify. Rozszerzenie musi być włączone w Spicetify: `spicetify config extensions augustus.js`.
val copySpicetifyExtension by tasks.registering(Copy::class) {
    from(tasks.named("jsBrowserProductionWebpack")) {
        include("augustus.js")
    }
    into(providers.environmentVariable("APPDATA").map { "$it/spicetify/Extensions" })
}

tasks.register<Exec>("installSpicetifyExtension") {
    group = "Augustus Extension"
    description = "Builds augustus.js, copies it to Spicetify's extensions and runs spicetify apply"

    dependsOn(copySpicetifyExtension)
    commandLine("spicetify", "apply")
}
