package me.qviperh.vAPIBridge.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class ExternalConfig(
    private val name: String,
    private val plugin: JavaPlugin
) {

    private val file: File
    var configuration: FileConfiguration
        private set

    init {
        plugin.saveResource(name, false)
        file = File(plugin.dataFolder.path, name)
        configuration = loadConfiguration()
    }

    private fun loadConfiguration(): FileConfiguration {
        if (!file.exists()) {
            throw IllegalStateException("File does not exist in current data folder. (${file.path})")
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    fun reload() {
        configuration = YamlConfiguration.loadConfiguration(file)
    }

    fun save() {
        try {
            configuration.save(file)
        } catch (exception: IOException) {
            plugin.logger.severe("Failed to save configuration file: $name")
            exception.printStackTrace()
        }
    }
}
