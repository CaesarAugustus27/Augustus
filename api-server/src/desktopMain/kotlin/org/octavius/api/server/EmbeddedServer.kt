package org.octavius.api.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.octavius.contract.ApiModule

class EmbeddedServer(private val apiModules: List<ApiModule>) {

    private val engine = embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json()
        }

        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            anyHost()
        }
        module(apiModules)
    }

    fun start() {
        engine.start(wait = true)
    }

    fun stop() {
        println("Stopping Ktor server gracefully...")
        // Dajemy serwerowi 1 sekundę na dokończenie aktywnych żądań
        // i maksymalnie 5 sekund na całkowite zamknięcie.
        engine.stop(1000, 5000)
        println("Ktor server stopped.")
    }
}

fun Application.module(apiModules: List<ApiModule>) {
    configureRouting(apiModules)
}

fun Application.configureRouting(apiModules: List<ApiModule>) {
    routing {
        // Podstawowy, powitalny endpoint
        get("/") {
            call.respondText("Hello from Augustus Server!")
        }

        // iterujemy po wszystkich modułach i prosimy je o instalację swoich ścieżek
        apiModules.forEach { module ->
            module.installRoutes(this)
        }
    }
}