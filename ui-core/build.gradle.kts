plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")
    js { browser() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Zależności Compose, które działają wszędzie

                api(composeLibs.runtime)
                api(composeLibs.foundation)
                api(composeLibs.material3)
                api(composeLibs.ui)
                api(composeLibs.components.resources)
                api(composeLibs.materialIconsExtended)


                // Inne współdzielone biblioteki
                api(libs.octavius.i18n.core)
                api(projects.sharedContract)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.octavius.driver)
                implementation(libs.kotlin.logging)
            }
        }
        val jsMain by getting
    }
}

octaviusI18n {
    generators {
        create("main") {
            targetPackage = "org.octavius.localization"
        }
    }
}
