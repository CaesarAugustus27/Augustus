plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
                implementation(projects.sharedContract)
                implementation(projects.uiCore)
                implementation(projects.navigation)
                implementation(composeLibs.runtime)
                implementation(composeLibs.foundation)
                implementation(composeLibs.ui)
                implementation(composeLibs.material3)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js) // Implementacja dla środowiska JS
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(projects.featureAsianMedia)
            }
        }
    }
}

octaviusI18n {
    generators {
        create("popup") {
            targetPackage = "org.octavius.extension.localization"
            objectName = "PopupTr"
        }
    }
}
