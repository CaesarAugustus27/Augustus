plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(projects.uiCore)
            implementation(projects.navigation)
            implementation(composeLibs.components.resources)

            implementation(libs.kotlinx.coroutines.core)
        }

        desktopMain.dependencies {

            implementation(projects.formEngine.core)
            implementation(projects.formEngine.database)
            implementation(projects.reportEngine)
            implementation(projects.featureContract)
            implementation(project.dependencies.platform(libs.koin.bom))

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}

octaviusI18n {
    generators {
        create("sandbox") {
            targetPackage = "org.octavius.modules.sandbox.localization"
            objectName = "SandboxTr"
        }
    }
}
