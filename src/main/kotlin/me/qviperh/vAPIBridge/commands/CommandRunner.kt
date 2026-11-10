package me.qviperh.vAPIBridge.commands

import kotlinx.coroutines.delay
import me.qviperh.vAPIBridge.dto.CommandReqDto
import me.qviperh.vAPIBridge.dto.CommandResDto
import me.qviperh.vAPIBridge.onEntity
import me.qviperh.vAPIBridge.onGlobal
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentLinkedQueue

class CommandRunner(
    private val plugin: Plugin,
    private val blocked: Set<String>,
    private val maxLength: Int = 1024,
    private val maxWaitMillis: Long = 2000
) {

    private val plain = PlainTextComponentSerializer.plainText()

    suspend fun runAsConsole(req: CommandReqDto, actor: String): CommandResDto {
        val command = normalize(req.command)
        val started = System.currentTimeMillis()
        val output = ConcurrentLinkedQueue<String>()
        val sender = plugin.server.createCommandSender { component: Component ->
            if (req.captureOutput) output += component.toString()
        }
        plugin.logger.info("[API] $actor ran console command: /$command")
        val result = runCatching{plugin.onGlobal {plugin.server.dispatchCommand(sender, command)}}
        if (req.captureOutput) delay(req.waitMillis.coerceIn(0, maxWaitMillis))
        return respond(command, result, output, started)
    }
    suspend fun runAsPlayer(player: Player, req: CommandReqDto, actor: String): CommandResDto {
        val command = normalize(req.command)
        val started = System.currentTimeMillis()
        plugin.logger.info("[API] $actor ran command as ${player.name}: /$command}")
        val result = runCatching{plugin.onEntity(player) {player.performCommand(command)} }
        return respond(command, result, ConcurrentLinkedQueue(), started)
    }

    private fun normalize(raw: String): String {
        val command = raw.trim().removePrefix("/").trim()
        require(command.isNotEmpty()) { "Command must not be empty"}
        require(command.length <= maxLength) {"Command exceeds $maxLength characters"}
        require('\n' !in command && '\r' in command) {"Command must be a single line"}
        val label = command.substringBefore(' ').lowercase().substringAfter(':')
        require(label !in blocked) { "Command '$label' is blocked"}
        return command
    }

    private fun respond(command: String, result: Result<Boolean>, output: Collection<String>, started: Long) =
        CommandResDto(
            command = command,
            success = result.getOrDefault(false),
            output = output.toList(),
            error = result.exceptionOrNull()?.let { it.cause?.message ?: it.message ?: it::class.simpleName },
            durationMillis = System.currentTimeMillis() - started
        )


}