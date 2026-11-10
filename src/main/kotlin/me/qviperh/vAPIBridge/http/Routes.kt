package me.qviperh.vAPIBridge.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.delay
import me.qviperh.vAPIBridge.VAPIBridge
import me.qviperh.vAPIBridge.commands.CommandRunner
import me.qviperh.vAPIBridge.dto.BasicResponseDto
import me.qviperh.vAPIBridge.dto.BroadcastDto
import me.qviperh.vAPIBridge.dto.CommandReqDto
import me.qviperh.vAPIBridge.dto.MessageDto
import me.qviperh.vAPIBridge.dto.MotdDto
import me.qviperh.vAPIBridge.dto.PlayerDto
import me.qviperh.vAPIBridge.dto.PluginInfoDto
import me.qviperh.vAPIBridge.dto.PluginsDto
import me.qviperh.vAPIBridge.dto.SaveReqDto
import me.qviperh.vAPIBridge.dto.SaveResDto
import me.qviperh.vAPIBridge.dto.ShutdownDto
import me.qviperh.vAPIBridge.metrics.PerformanceCollector
import me.qviperh.vAPIBridge.metrics.ServerInfoCollector
import me.qviperh.vAPIBridge.onEntity
import me.qviperh.vAPIBridge.onGlobal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.Locale.getDefault
import java.util.UUID


private val mini = MiniMessage.miniMessage()

fun Route.healthRoutes(plugin: Plugin) {
    get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "ok")) }
    get("/v1/info") {call.respond(HttpStatusCode.OK, PluginInfoDto(plugin.name, plugin.pluginMeta.mainClass, plugin.pluginMeta.version, plugin.pluginMeta.apiVersion)) }
}

fun Route.serverRoutes(plugin: VAPIBridge, info: ServerInfoCollector, perf: PerformanceCollector, runner: CommandRunner) {
    route("/v1/server") {
        get {
            call.respond(HttpStatusCode.OK, info.collect())
        }
        get("/performance") {
            call.respond(HttpStatusCode.OK, perf.collect())
        }
        post("/broadcast") {
            val body = call.receive<BroadcastDto>()
            val component = mini.deserialize(body.text)
            plugin.onGlobal { plugin.server.broadcast(component) }
            call.respond(HttpStatusCode.OK)
        }
        post("/command") {
            val req = call.receive<CommandReqDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            val res = runner.runAsConsole(req, actor)
            call.respond(if (res.error == null) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity, res)
        }

        post("/save") {
            val req = call.receive<SaveReqDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            val flush = req.flush
            plugin.logger.info("[API] $actor ran save-all ${if (flush) "" else "flush"}")
            val command = if (flush) "save-all flush" else "save-all"
            val res = runner.runAsConsole(
                CommandReqDto(command, true),
                actor
            )
            call.respond(if(res.success) HttpStatusCode.OK else HttpStatusCode.ExpectationFailed, SaveResDto(res.success, flush, res.output, res.error, res.durationMillis))
        }

        get("/motd") {
            call.respond(HttpStatusCode.OK, MotdDto(plugin.server.motd().toString()))
        }
        post("/motd") {
            val req = call.receive<MotdDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor changed motd to $req.motd")
            plugin.onGlobal{
                plugin.server.motd(Component.text(req.motd))
            }
            call.respond(HttpStatusCode.OK, BasicResponseDto("ok"))
        }

        post("/shutdown") {
            val req = call.receive<ShutdownDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor commanded a shutdown")
            if (req.message != null) {
                if (req.delaySeconds != null){
                    delay(req.delaySeconds * 1000)
                }
                plugin.onGlobal{
                    plugin.server.onlinePlayers.forEach {
                        player ->
                        player.kick(Component.text(req.message))
                    }
                }
                call.respond(HttpStatusCode.OK, BasicResponseDto("ok"))
                plugin.onGlobal{
                    plugin.server.shutdown()
                }
            }
        }

        post("/restart") {
            val req = call.receive<ShutdownDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor commanded a restart")
            if (req.message != null) {
                if (req.delaySeconds != null){
                    delay(req.delaySeconds * 1000)
                }
                plugin.onGlobal{
                    plugin.server.onlinePlayers.forEach {
                        player ->
                        player.kick(Component.text(req.message))
                    }
                }
                call.respond(HttpStatusCode.OK, BasicResponseDto("ok"))
                plugin.onGlobal {
                    plugin.server.restart()
                }
            }
        }
        get("/plugins") {
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor querying plugins")
            val plgs = plugin.server.pluginManager.plugins.map {PluginInfoDto(it.name, it.pluginMeta.mainClass, it.pluginMeta.version, it.pluginMeta.apiVersion)}
            call.respond(HttpStatusCode.OK, PluginsDto(plgs))
        }

        get("/plugins/{name}") {
            val name = call.parameters["name"]
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor querying plugin $name")
            if (name != null) {
                val pl = plugin.server.pluginManager.getPlugin(name)
                if (pl != null) {
                    val dto = PluginsDto(listOf(PluginInfoDto(pl.name, pl.pluginMeta.mainClass, pl.pluginMeta.version, pl.pluginMeta.apiVersion)))
                    call.respond(HttpStatusCode.OK, dto)

                } else {
                    call.respond(HttpStatusCode.NotFound, BasicResponseDto("Plugin not found!"))
                }
            }
        }



    }
}

fun Route.playerRoutes(plugin: VAPIBridge, runner: CommandRunner) {
    route("/v1/players") {
        get {
            val players = plugin.server.onlinePlayers
            val world = call.parameters["world"]
            if (world != null) {
                call.respond(HttpStatusCode.OK, players.map {player -> if (player.location.world.name == world) {
                    PlayerDto.fromPlayer(player)
                }
                })
                return@get
            }
            call.respond(HttpStatusCode.OK, players.map {
                PlayerDto.fromPlayer(it)
            })
        }
        get("/{name}") {
            val name = call.parameters["name"]
            if (name == null)  {
                call.respond(HttpStatusCode.NotFound, BasicResponseDto("Player not found!"))
                return@get
            }
            val oplayer = plugin.server.getOfflinePlayer(name)
            if (oplayer.player == null)  {
                call.respond(HttpStatusCode.NotFound, BasicResponseDto("Player not found!"))
                return@get
            }
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor querying player $name")
            call.respond(HttpStatusCode.OK, PlayerDto.fromOfflinePlayer(oplayer))

        }
        get("/{uuid}") {
            val param = call.parameters["uuid"]
            if (param == null) {
                call.respond(HttpStatusCode.BadRequest, BasicResponseDto("Bad uuid!"))
                return@get
            }
            val offlinePlayer = plugin.server.getOfflinePlayer(param)
            call.respond(
                HttpStatusCode.OK,
                PlayerDto.fromOfflinePlayer(offlinePlayer)
            )

        }
        post("/{uuid}") {
            val param = call.parameters["uuid"]
            if (param == null) {
                call.respond(HttpStatusCode.BadRequest, BasicResponseDto("Bad uuid!"))
                return@post
            }

            val req = call.receive<PlayerDto>()

            val offlinePlayer = plugin.server.getOfflinePlayer(param)
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            plugin.logger.info("[API] $actor changed player $req.name")
            if (offlinePlayer.isOnline && offlinePlayer.player != null) {
                plugin.onEntity(offlinePlayer.player!!) {
                    req.applyTo(offlinePlayer.player!!)
                }
                call.respond(HttpStatusCode.OK, PlayerDto.fromOfflinePlayer(offlinePlayer))
                return@post
            }
            call.respond(HttpStatusCode.PreconditionFailed,BasicResponseDto("Player is offline!"))
        }


        post("/{uuid}/message") {
            val player = plugin.requirePlayer(call.parameters["uuid"])
            val component = mini.deserialize(call.receive<MessageDto>().text)
            plugin.onEntity(player) { player.sendMessage(component) }
            call.respond(HttpStatusCode.NoContent)
        }
        post("/{uuid}/command") {
            val player = plugin.requirePlayer(call.parameters["uuid"])
            val req = call.receive<CommandReqDto>()
            val actor = call.principal<UserIdPrincipal>()?.name ?: "unknown"
            val res = runner.runAsPlayer(player, req, actor)
            call.respond(if (res.error == null) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity, res)
        }
    }
}

private fun VAPIBridge.requirePlayer(raw: String?): Player {
    val uuid = UUID.fromString(raw ?: throw IllegalArgumentException("Missing uuid"))
    return server.getPlayer(uuid) ?: throw NotFoundException("Player not online")
}

fun PlayerDto.applyTo(player: Player?) {
    health.ifPresent {player?.health = it.coerceIn(0.0, player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)}
    foodLevel.ifPresent {player?.foodLevel = it}
    gameMode.ifPresent {player?.gameMode = GameMode.valueOf(it.uppercase(getDefault()))}
    saturation.ifPresent {player?.saturation = it}
    level.ifPresent {player?.level = it}
    exp?.ifPresent {player?.exp = it}
    allowFlight.ifPresent {player?.allowFlight = it}
    walkSpeed.ifPresent {player?.walkSpeed = it}
    flySpeed.ifPresent {player?.flySpeed = it}
}

inline fun <T> T?.ifPresent(block: (T) -> Unit) {
    if (this != null) block(this)
}