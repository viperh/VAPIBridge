package me.qviperh.vAPIBridge

import org.bukkit.configuration.file.FileConfiguration

data class Settings(val host: String, val port: Int, val apiKey: String) {
    companion object {
        fun load(config: FileConfiguration) = Settings(
            host = config.getString("http.host") ?: "127.0.0.1",
            port = config.getInt("http.port", 8085),
            apiKey = config.getString("http.api-key") ?: "change-me"
        )
    }
}