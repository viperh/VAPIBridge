package me.qviperh.vAPIBridge.http

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.qviperh.vAPIBridge.Settings
import me.qviperh.vAPIBridge.VAPIBridge
import me.qviperh.vAPIBridge.events.EventHub
import me.viperh.vHB.http.eventRoutes
import me.viperh.vHB.http.healthRoutes
import me.viperh.vHB.http.playerRoutes
import me.viperh.vHB.http.serverRoutes
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class ErrorDto(val error: String)

class NotFoundException(message: String) : RuntimeException(message)

fun Application.httpModule(plugin: VAPIBridge, hub: EventHub, settings: Settings) {
    val expected = settings.apiKey.toByteArray()

    install(ContentNegotiation) { json(ApiJson) }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 64 * 1024
    }

    install(StatusPages) {
        exception<NotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ErrorDto(e.message ?: "not found"))
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto(e.message ?: "bad request"))
        }
        exception<IllegalStateException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ErrorDto(e.message ?: "conflict"))
        }
        exception<Throwable> { call, e ->
            plugin.logger.warning("Unhandled HTTP error: ${e::class.simpleName}: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal error"))
        }
    }

    install(Authentication) {
        bearer("api-key") {
            authHeader { call ->
                call.request.queryParameters["token"]?.let { HttpAuthHeader.Single("Bearer", it) }
                    ?: call.request.parseAuthorizationHeader()
            }
            authenticate { credential ->
                if (MessageDigest.isEqual(credential.token.toByteArray(), expected)) UserIdPrincipal("api") else null
            }
        }
    }

    routing {
        healthRoutes()
        authenticate("api-key") {
            serverRoutes(plugin)
            playerRoutes(plugin)
            eventRoutes(hub)
        }
    }
}
