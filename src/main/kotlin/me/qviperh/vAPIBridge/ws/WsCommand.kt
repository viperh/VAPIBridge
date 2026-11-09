package me.qviperh.vAPIBridge.ws

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.qviperh.vAPIBridge.events.EventHub
import me.qviperh.vAPIBridge.events.Subscriber

@Serializable
data class WsCommand(val op: String, val topics: List<String> = emptyList())

fun Route.eventSocket(hub: EventHub) {
    webSocket("/v1/events") {
        val outbox = Channel<String>(256, BufferOverflow.DROP_OLDEST)
        val subscriber = Subscriber { outbox.trySend(it).isSuccess }
        val active = mutableSetOf<String>()
        val writer = launch { for (msg in outbox) send(Frame.Text(msg)) }
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val cmd = runCatching { Json.decodeFromString<WsCommand>(frame.readText()) }.getOrNull() ?: continue
                when (cmd.op) {
                    "subscribe" -> cmd.topics.forEach { topic ->
                        runCatching { hub.subscribe(topic, subscriber) }
                            .onSuccess { active += topic }
                            .onFailure { send(Frame.Text("""{"error":"unknown topic","topic":"$topic"}""")) }
                    }
                    "unsubscribe" -> cmd.topics.forEach { hub.unsubscribe(it, subscriber); active -= it }
                }
            }
        } finally {
            active.forEach { hub.unsubscribe(it, subscriber) }
            writer.cancel()
        }
    }
}