package me.qviperh.vAPIBridge.events

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

fun interface Subscriber {
    fun offer(message: String): Boolean
}

class EventHub(private val plugin: Plugin, val registry: EventRegistry) {
    private class Topic(val listener: Listener, val subscribers: MutableSet<Subscriber>)

    private val topics = ConcurrentHashMap<String, Topic>()

    fun subscribe(topic: String, subscriber: Subscriber) {
        val def = registry[topic] ?: throw IllegalArgumentException("Unknown topic: $topic")
        topics.compute(topic) { _, existing ->
            (existing ?: Topic(register(def), ConcurrentHashMap.newKeySet())).also { it.subscribers += subscriber }
        }
    }

    fun unsubscribe(topic: String, subscriber: Subscriber) {
        topics.computeIfPresent(topic) { _, t ->
            t.subscribers -= subscriber
            if (t.subscribers.isEmpty()) {
                HandlerList.unregisterAll(t.listener)
                null
            } else t
        }
    }

    fun shutdown() {
        topics.values.forEach { HandlerList.unregisterAll(it.listener) }
        topics.clear()
    }

    private fun register(def: EventDef<*>): Listener {
        val listener = object : Listener {}
        plugin.server.pluginManager.registerEvent(
            def.type.java, listener, EventPriority.MONITOR,
            { _, event -> dispatch(def, event) },
            plugin, true
        )
        return listener
    }

    private fun dispatch(def: EventDef<*>, event: Event) {
        val subs = topics[def.topic]?.subscribers ?: return
        if (subs.isEmpty()) return
        val data = def.encode(event) ?: return
        val message = buildJsonObject {
            put("topic", def.topic)
            put("ts", System.currentTimeMillis())
            put("data", data)
        }.toString()
        subs.forEach { it.offer(message) }
    }
}