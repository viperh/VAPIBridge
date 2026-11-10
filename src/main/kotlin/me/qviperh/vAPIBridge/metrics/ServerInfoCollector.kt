package me.qviperh.vAPIBridge.metrics

import me.qviperh.vAPIBridge.dto.NetworkDto
import me.qviperh.vAPIBridge.dto.PlayerCountDto
import me.qviperh.vAPIBridge.dto.PluginInfoDto
import me.qviperh.vAPIBridge.dto.RuntimeInfoDto
import me.qviperh.vAPIBridge.dto.ServerDto
import me.qviperh.vAPIBridge.dto.ServerSettingsDto
import me.qviperh.vAPIBridge.onGlobal
import org.bukkit.plugin.Plugin
import java.lang.management.ManagementFactory
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class ServerInfoCollector(private val plugin: Plugin) {
    suspend fun collect(): ServerDto = plugin.onGlobal {
        val s = plugin.server
        val motd = s.motd()
        val rt = ManagementFactory.getRuntimeMXBean()
        val meta = plugin.pluginMeta

        ServerDto(
            name = s.name,
            platform = Platforms.current,
            version = s.version,
            bukkitVersion = s.bukkitVersion,
            minecraftVersion = s.minecraftVersion,
            plugin = PluginInfoDto(meta.name, meta.mainClass, meta.version, meta.apiVersion ?: "unknown"),
            motd = MiniMessage.miniMessage().serialize(motd),
            motdPlain = PlainTextComponentSerializer.plainText().serialize(motd),
            players = PlayerCountDto(s.onlinePlayers.size, s.maxPlayers),
            network = NetworkDto(s.ip.ifBlank {"0.0.0.0"}, s.port, s.onlineMode),
            settings = ServerSettingsDto(
                defaultGameMode = s.defaultGameMode.name.lowercase(),
                hardcore = s.isHardcore,
                whitelist = s.hasWhitelist(),
                whitelistEnforced = s.isWhitelistEnforced,
                viewDistance = s.viewDistance,
                simulationDistance = s.simulationDistance,
                allowNether = s.allowNether,
                allowEnd = s.allowEnd
            ),
            worlds = s.worlds.map {it.name},
            runtime = RuntimeInfoDto(
                javaVersion = System.getProperty("java.version"),
                javaVendor = System.getProperty("java.vendor"),
                os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
                arch = System.getProperty("os.arch"),
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                startedAt = rt.startTime,
                uptimeMillis = rt.uptime
            )
        )
    }
}