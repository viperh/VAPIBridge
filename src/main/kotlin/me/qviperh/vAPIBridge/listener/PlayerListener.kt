package me.qviperh.vAPIBridge.listener

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class PlayerListener(private val plugin: JavaPlugin) : BaseListener(plugin) {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.sendMessage(
            Component.text("Welcome, ").color(NamedTextColor.GREEN)
                .append(Component.text(player.name).color(NamedTextColor.GOLD))
                .append(Component.text("!").color(NamedTextColor.GREEN))
        )
        plugin.logger.info("Player joined: ${player.name}")
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.logger.info("Player quit: ${event.player.name}")
    }
}
