plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Kontrakty wspólne dla aplikacji i wtyczki.
 *
 * Korzysta z nich content script, więc moduł świadomie NIE zależy od Compose ani od Ktora.
 */
kotlin {
    jvm("desktop")
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
