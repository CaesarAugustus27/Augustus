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
        val desktopTest by getting

        commonMain.dependencies {
            implementation(projects.uiCore)

            api(libs.octavius.i18n.core)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.kotlinx.datetime)
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
        create("form") {
            targetPackage = "io.github.octaviusframework.form.localization"
            objectName = "FormTr"
        }
    }
}
