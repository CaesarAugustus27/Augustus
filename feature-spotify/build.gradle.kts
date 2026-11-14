plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // API kopii kosza i polubionych, bez ekranów, więc bez Compose i bez tłumaczeń
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
        }

        desktopMain.dependencies {
            implementation(projects.featureContract)
            implementation(projects.navigation)
            implementation(libs.octavius.client)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }

        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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
