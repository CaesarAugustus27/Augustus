plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(projects.browserExtension.chromeApi)
                implementation(projects.apiContract)
                implementation(projects.featureAsianMedia)
                implementation(projects.featureGames)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}