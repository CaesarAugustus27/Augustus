plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // API kopii kosza, bez ekranów, więc bez Compose i bez tłumaczeń
    jvm("desktop")
    // Rozszerzenie Spicetify; DTO API dzieli z aplikacją przez commonMain
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "trashbin.js"
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

// Trashbin -> katalog rozszerzeń Spicetify (%APPDATA%\spicetify\Extensions), potem `spicetify apply`, które
// restartuje Spotify. Rozszerzenie musi być włączone w Spicetify: `spicetify config extensions trashbin.js`.
val copyTrashbin by tasks.registering(Copy::class) {
    from(tasks.named("jsBrowserProductionWebpack")) {
        include("trashbin.js")
    }
    into(providers.environmentVariable("APPDATA").map { "$it/spicetify/Extensions" })
}

tasks.register<Exec>("installTrashbin") {
    group = "Augustus Extension"
    description = "Builds trashbin.js, copies it to Spicetify's extensions and runs spicetify apply"

    dependsOn(copyTrashbin)
    commandLine("spicetify", "apply")
}
