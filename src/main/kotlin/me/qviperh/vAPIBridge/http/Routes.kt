package me.viperh.vHB.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import me.qviperh.vAPIBridge.VAPIBridge
import me.qviperh.vAPIBridge.http.NotFoundException
import me.qviperh.vAPIBridge.onEntity
import me.qviperh.vAPIBridge.onGlobal
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.UUID

@Serializable
data class ServerDto(val name: String, val version: String, val minecraftVersion: String, val online: Int, val maxPlayers: Int)

@Serializable
data class PlayerDto(val uuid: String, val name: String, val world: String, val x: Double, val y: Double, val z: Double)

@Serializable
data class MessageDto(val text: String)

@Serializable
data class BroadcastDto(val text: String)

private val mini = MiniMessage.miniMessage()

fun Route.healthRoutes() {
    get("/health") { call.respond(mapOf("status" to "ok")) }
}

fun Route.serverRoutes(plugin: VAPIBridge) {
    route("/v1/server") {
        get {
            val dto = plugin.onGlobal {
                val s = plugin.server
                ServerDto(s.name, s.version, s.minecraftVersion, s.onlinePlayers.size, s.maxPlayers)
            }
            call.respond(dto)
        }
        post("/broadcast") {
            val body = call.receive<BroadcastDto>()
            val component = mini.deserialize(body.text)
            plugin.onGlobal { plugin.server.broadcast(component) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.playerRoutes(plugin: VAPIBridge) {
    route("/v1/players") {
        get {
            val players = plugin.server.onlinePlayers.map { PlayerDto(it.uniqueId.toString(), it.name, it.world.name, it.location.x, it.location.y, it.location.z) }
            call.respond(players)
        }
        get("/{uuid}") {
            val player = plugin.requirePlayer(call.parameters["uuid"])
            call.respond(plugin.onEntity(player) { PlayerDto(player.uniqueId.toString(), player.name, player.world.name, player.location.x, player.location.y, player.location.z) })
        }
        post("/{uuid}/message") {
            val player = plugin.requirePlayer(call.parameters["uuid"])
            val component = mini.deserialize(call.receive<MessageDto>().text)
            plugin.onEntity(player) { player.sendMessage(component) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun VAPIBridge.requirePlayer(raw: String?): Player {
    val uuid = UUID.fromString(raw ?: throw IllegalArgumentException("Missing uuid"))
    return server.getPlayer(uuid) ?: throw NotFoundException("Player not online")
}