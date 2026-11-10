plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Samo API dla rozszerzenia Spicetify ze spicetify/, bez ekranów, więc bez Compose i bez tłumaczeń
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(projects.featureContract)
            implementation(projects.navigation)
            implementation(libs.octavius.client)
            implementation(libs.kotlinx.serialization.json)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}

// Trashbin -> katalog rozszerzeń Spicetify (%APPDATA%\spicetify\Extensions), potem `spicetify apply`, które
// restartuje Spotify. Rozszerzenie musi być włączone w Spicetify: `spicetify config extensions trashbin.js`.
val copyTrashbin by tasks.registering(Copy::class) {
    from("spicetify/trashbin.js")
    into(providers.environmentVariable("APPDATA").map { "$it/spicetify/Extensions" })
}

tasks.register<Exec>("installTrashbin") {
    group = "Augustus Extension"
    description = "Copies trashbin.js to Spicetify's extensions and runs spicetify apply"

    dependsOn(copyTrashbin)
    commandLine("spicetify", "apply")
}
