package me.qviperh.vAPIBridge

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import org.bukkit.plugin.java.JavaPlugin
import me.qviperh.vAPIBridge.events.EventHub
import me.qviperh.vAPIBridge.events.defaultRegistry
import me.qviperh.vAPIBridge.http.httpModule

class VAPIBridge : JavaPlugin() {

    private var http: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    lateinit var hub: EventHub
        private set

    override fun onEnable() {
        try {
            saveDefaultConfig()
            val settings = Settings.load(config)
            if (settings.apiKey == "change-me") {
                logger.warning("http.api-key is still the default value, change it in the config!")
            }

            hub = EventHub(this, defaultRegistry())
            http = startHttp(settings)


            instance = this


            logger.info("$name has been enabled!")
        } catch (exception: Exception) {
            logger.severe("An error occurred during plugin initialization. Disabling plugin....")
            exception.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        http?.stop(gracePeriodMillis=1000, timeoutMillis=3000)
        http = null
        if (::hub.isInitialized) hub.shutdown()
        logger.info("$name has been disabled!")
    }

    private fun startHttp(settings: Settings): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        return try {
            embeddedServer(CIO, port = settings.port, host = settings.host) {
                httpModule(this@VAPIBridge, hub, settings)
            }.start(wait = false).also {
                logger.info("HTTP API listening on ${settings.host}:${settings.port}")
            }
        } catch (e: Exception) {
            logger.severe("Failed to start HTTP server on ${settings.host}:${settings.port}: ${e.message}")
            null
        } finally {
            thread.contextClassLoader = previous
        }
    }


    companion object {
        lateinit var instance: VAPIBridge
            private set
    }
}
