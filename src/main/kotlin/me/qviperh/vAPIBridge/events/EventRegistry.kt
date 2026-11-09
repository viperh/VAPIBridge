package me.qviperh.vAPIBridge.events

import kotlinx.serialization.json.*
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class EventDef<T : Event>(val topic: String, val type: KClass<T>, private val serializer: (T) -> JsonObject) {
    fun encode(event: Event): JsonObject? = type.safeCast(event)?.let(serializer)
}

class EventRegistry {
    private val defs = mutableMapOf<String, EventDef<*>>()

    fun <T : Event> register(topic: String, type: KClass<T>, serializer: (T) -> JsonObject) {
        defs[topic] = EventDef(topic, type, serializer)
    }

    operator fun get(topic: String): EventDef<*>? = defs[topic]
    val topics: Set<String> get() = defs.keys
}

fun defaultRegistry() = EventRegistry().apply {
    register("player.join", PlayerJoinEvent::class) {
        buildJsonObject {
            put("uuid", it.player.uniqueId.toString())
            put("name", it.player.name)
        }
    }
    register("player.quit", PlayerQuitEvent::class) {
        buildJsonObject {
            put("uuid", it.player.uniqueId.toString())
            put("name", it.player.name)
        }
    }
    register("block.break", BlockBreakEvent::class) { e ->
        buildJsonObject {
            put("player", e.player.uniqueId.toString())
            put("block", e.block.type.name)
            put("world", e.block.world.name)
            put("x", e.block.x)
            put("y", e.block.y)
            put("z", e.block.z)
        }
    }

    register("block.place", BlockPlaceEvent::class) { e ->
        buildJsonObject {
            put("player", e.player.uniqueId.toString())
            put("block", e.block.type.name)
            put("world", e.block.world.name)
            put("x", e.block.x)
            put("y", e.block.y)
            put("z", e.block.z)
        }
    }
}