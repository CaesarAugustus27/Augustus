plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                // Tło tylko przenosi dane między stroną a aplikacją albo API Steama, więc nie zna ani DTO,
                // ani stron - opis serii składa i odpowiedzi Steama czyta content script, a decyzje
                // podejmuje aplikacja.
                implementation(projects.browserExtension.chromeApi)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
