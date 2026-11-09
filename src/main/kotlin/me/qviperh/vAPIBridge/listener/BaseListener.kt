package me.qviperh.vAPIBridge.listener

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

abstract class BaseListener(plugin: JavaPlugin) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }
}
