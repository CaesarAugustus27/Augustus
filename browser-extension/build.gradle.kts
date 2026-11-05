plugins {
    base
}

// Wtyczka -> build/extension, do wczytania w Chrome jako rozpakowane rozszerzenie
tasks.register<Sync>("assembleBrowserExtension") {
    group = "Augustus Extension"
    description = "Assembles the final browser extension into build/extension"

    // Popup w całości: obok popup.js leżą wasm Skiko i chunk webpacka. Wyjście zadania obejmuje też
    // wygenerowany config webpacka.
    from(project(":browser-extension:popup").tasks.named("jsBrowserProductionWebpack")) {
        exclude("webpack.config.js")
    }
    from(project(":browser-extension:content-script").tasks.named("jsBrowserProductionWebpack")) {
        include("content-script.js")
    }
    from(project(":browser-extension:background").tasks.named("jsBrowserProductionWebpack")) {
        include("background.js")
    }
    // Tłumaczenia z tego katalogu są wkompilowane w PopupTr
    from(project(":browser-extension:popup").file("src/jsMain/resources")) {
        include("manifest.json", "popup.html")
    }
    into(layout.buildDirectory.dir("extension"))
}
