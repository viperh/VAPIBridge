# VAPIBridge

> ⚠️ **Work in progress.** VAPIBridge is still in early development. Endpoints, payloads and config keys may change without notice. Don't use it on a production server yet.

A [Folia](https://papermc.io/software/folia) / Paper plugin that runs an HTTP + WebSocket API inside your Minecraft server. External apps (bots, dashboards, web panels) can read server and player state, send messages, and subscribe to live game events.

## Features

- Embedded [Ktor](https://ktor.io) server (CIO engine) that starts and stops with the plugin
- Bearer-token API key authentication
- REST endpoints for server info, online players, broadcasts and direct messages
- WebSocket event stream with per-topic subscriptions
- Folia-safe: all game access goes through the global, region and entity schedulers
- Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting

## Requirements

- Folia or Paper 1.21+
- Java 21

## Build

```sh
mvn clean package
```

The jar is written to `target/VAPIBridge-1.0.0.jar`. Put it in your server's `plugins/` folder. Kotlin and Ktor are downloaded by the server at startup through `plugin.yml` `libraries`.

## Configuration

`plugins/VAPIBridge/config.yml` is created on first start:

```yaml
http:
  host: 127.0.0.1   # bind address; use 0.0.0.0 to expose it outside the machine
  port: 8085
  api-key: change-me

grpc:               # reserved, not implemented yet
  host: 127.0.0.1
  port: 8086
  api-key: change-me
```

**Change `http.api-key` before you expose the API.** The plugin logs a warning while it is still `change-me`.

## Authentication

Every endpoint except `/health` needs the API key, sent either way:

- Header: `Authorization: Bearer <api-key>`
- Query parameter: `?token=<api-key>` (handy for WebSocket clients that can't set headers)

## REST API

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/health` | | `{"status":"ok"}` (no auth) |
| `GET` | `/v1/server` | | Server name, version, Minecraft version, online count, max players |
| `POST` | `/v1/server/broadcast` | `{"text":"<MiniMessage>"}` | `204` |
| `GET` | `/v1/players` | | List of online players |
| `GET` | `/v1/players/{uuid}` | | A single online player |
| `POST` | `/v1/players/{uuid}/message` | `{"text":"<MiniMessage>"}` | `204` |
| `GET` | `/v1/topics` | | Names of the available event topics |

Player objects look like this:

```json
{ "uuid": "…", "name": "Steve", "world": "world", "x": 12.5, "y": 64.0, "z": -3.2 }
```

Errors come back as `{"error":"…"}` with status `400` (bad input), `404` (player not online), `409` or `500`.

### Example

```sh
curl -H "Authorization: Bearer $KEY" http://127.0.0.1:8085/v1/players

curl -X POST -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
     -d '{"text":"<gold>Server restarting in 5 minutes"}' \
     http://127.0.0.1:8085/v1/server/broadcast
```

## WebSocket events

Connect to `ws://<host>:<port>/v1/events?token=<api-key>` and send JSON commands:

```json
{ "op": "subscribe",   "topics": ["player.join", "block.break"] }
{ "op": "unsubscribe", "topics": ["block.break"] }
{ "op": "list" }
```

The server replies with your current subscriptions or an error:

```json
{ "type": "subscribed", "topics": ["player.join"] }
{ "type": "error", "topics": ["foo.bar"], "error": "unknown topics" }
```

Events arrive like this:

```json
{ "topic": "player.join", "ts": 1762686000000, "data": { "uuid": "…", "name": "Steve" } }
```

### Topics

| Topic | `data` fields |
|---|---|
| `player.join` | `uuid`, `name` |
| `player.quit` | `uuid`, `name` |
| `block.break` | `player`, `block`, `world`, `x`, `y`, `z` |
| `block.place` | `player`, `block`, `world`, `x`, `y`, `z` |

The plugin only registers a Bukkit listener for a topic while at least one client is subscribed to it. Each connection buffers up to 256 messages; slow clients lose the oldest ones first.

### Adding a topic

Register it in `defaultRegistry()` in [`EventRegistry.kt`](src/main/kotlin/me/qviperh/vAPIBridge/events/EventRegistry.kt):

```kotlin
register("player.chat", AsyncChatEvent::class) { e ->
    buildJsonObject {
        put("uuid", e.player.uniqueId.toString())
        put("message", PlainTextComponentSerializer.plainText().serialize(e.message()))
    }
}
```

## Project layout

```
src/main/kotlin/me/qviperh/vAPIBridge/
  VAPIBridge.kt        # plugin entry point, starts/stops the HTTP server
  Settings.kt          # reads http.* from config.yml
  Schedulers.kt        # onGlobal / onRegion / onEntity suspend helpers for Folia
  events/              # EventHub (subscriptions) and EventRegistry (topics)
  http/                # Ktor module, auth, REST routes, WebSocket handler
  config/              # ExternalConfig helper for extra YAML files
  listener/            # Bukkit listener scaffolding
```

## Roadmap

- gRPC API (the `grpc` config section is reserved for it)
- More event topics and endpoints

## License

[MIT](LICENSE)
