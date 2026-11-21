plugins {
    alias(libs.plugins.octaviusI18n)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // This module targets desktop only
    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            api(projects.formEngine.core)
            implementation(projects.uiCore)

            api(libs.octavius.client)

            implementation(libs.kotlinx.coroutines.core)
        }

        desktopMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))

            implementation(libs.koin.core)
        }

        desktopTest.dependencies {
            implementation(libs.junit.jupiter.api)
            runtimeOnly(libs.junit.jupiter.engine)
            runtimeOnly(libs.junit.platform.launcher)
            implementation(libs.mockk)
            implementation(libs.assertj.core)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

octaviusI18n {
    generators {
        create("formDatabase") {
            targetPackage = "io.github.octaviusframework.form.database.localization"
            objectName = "FormDatabaseTr"
        }
    }
}
