package me.viperh.vHB.http

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.qviperh.vAPIBridge.events.EventHub
import me.qviperh.vAPIBridge.events.Subscriber
import me.qviperh.vAPIBridge.http.ApiJson

@Serializable
data class WsCommand(val op: String, val topics: List<String> = emptyList())

fun Route.eventRoutes(hub: EventHub) {
    get("/v1/topics") { call.respond(hub.registry.topics.toList()) }

    webSocket("/v1/events") {
        val outbox = Channel<String>(256, BufferOverflow.DROP_OLDEST)
        val subscriber = Subscriber { outbox.trySend(it).isSuccess }
        val active = mutableSetOf<String>()
        val writer = launch { for (msg in outbox) send(Frame.Text(msg)) }

        fun reply(type: String, topics: Collection<String>, error: String? = null) = buildJsonObject {
            put("type", type)
            putJsonArray("topics") { topics.forEach { add(it) } }
            if (error != null) put("error", error)
        }.toString()

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val cmd = runCatching { ApiJson.decodeFromString<WsCommand>(frame.readText()) }.getOrNull()
                if (cmd == null) {
                    outbox.trySend(reply("error", emptyList(), "invalid command"))
                    continue
                }
                when (cmd.op) {
                    "subscribe" -> {
                        val (known, unknown) = cmd.topics.partition { hub.registry[it] != null }
                        known.filter { it !in active }.forEach { hub.subscribe(it, subscriber); active += it }
                        outbox.trySend(reply("subscribed", active))
                        if (unknown.isNotEmpty()) outbox.trySend(reply("error", unknown, "unknown topics"))
                    }
                    "unsubscribe" -> {
                        cmd.topics.filter { it in active }.forEach { hub.unsubscribe(it, subscriber); active -= it }
                        outbox.trySend(reply("subscribed", active))
                    }
                    "list" -> outbox.trySend(reply("subscribed", active))
                    else -> outbox.trySend(reply("error", emptyList(), "unknown op: ${cmd.op}"))
                }
            }
        } finally {
            active.forEach { hub.unsubscribe(it, subscriber) }
            outbox.close()
            writer.cancel()
        }
    }
}