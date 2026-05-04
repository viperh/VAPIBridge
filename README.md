# MCTemplate

A Kotlin starter template for [Folia](https://papermc.io/software/folia) / Paper plugins (API 1.21). Comes wired with auto-discovered commands, a subcommand registry, an event listener scaffold, an external YAML config helper, and an optional HikariCP + PostgreSQL pool.

## Stack

- Kotlin 2.1.0, Java 21
- Folia API `1.21.11-R0.1-SNAPSHOT` (PaperMC repo)
- Maven (shade plugin, `minimizeJar`, with `org.postgresql` and `com.zaxxer.hikari` relocated under `<groupId>.<artifact>.libs`)
- HikariCP 7.0.2, PostgreSQL JDBC 42.7.10

## Build

```sh
mvn clean package
```

The shaded jar lands at `target/MCTemplate-1.0.0.jar`. Drop it into your server's `plugins/` directory.

## Quick start — rename the template

`setup.ps1` rewrites every package, import, the main class, the Maven coordinates, and `plugin.yml` in one shot, then deletes itself.

```powershell
.\setup.ps1 -GroupId com.example -PluginName CoolPlugin -Description "..." -Author "you"
```

After it runs, re-import the project in your IDE.

## Project layout

```
src/main/kotlin/org/me/qviperh/mCTemplate/
  MCTemplate.kt              # JavaPlugin entrypoint
  command/
    AsyncCommand.kt          # base class — typed sender + permission gating
    AsyncCommandManager.kt   # reflection-based command discovery
    CommandMetadata.kt       # @CommandMetadata annotation
    CommandWrapper.kt        # bridges AsyncCommand to Bukkit's CommandExecutor
    SubCommand.kt
    SubCommandRegistry.kt
    example/                 # PingCommand, HelpCommand, TestCommand + subs
  listener/
    BaseListener.kt          # auto-registers itself with the plugin manager
    PlayerListener.kt
  config/
    ExternalConfig.kt        # load/reload/save arbitrary YAML files
  database/
    DatabaseManager.kt       # HikariCP pool + executeAsync / queryAsync
src/main/resources/
  plugin.yml
  config.yml
```

## Adding a command

Annotate any subclass of `AsyncCommand<T>` with `@CommandMetadata` and drop it under the `command/` package — `AsyncCommandManager` scans the plugin jar (or the classes dir during dev) on enable and registers everything it finds.

```kotlin
@CommandMetadata(
    command = "ping",
    aliases = ["pong"],
    description = "Pings the server",
    usage = "/ping",
    permission = "mctemplate.ping"
)
class PingCommand : AsyncCommand<CommandSender>(CommandSender::class.java) {
    override fun execute(sender: CommandSender, args: Array<String>) {
        sender.sendMessage(Component.text("Pong!").color(NamedTextColor.GREEN))
    }
    override fun tabComplete(sender: CommandSender, args: Array<String>) = emptyList<String>()
}
```

Type the sender (`AsyncCommand<Player>`) to restrict execution — non-matching senders get a red "this command can only be executed by..." message automatically. Permission checks are also handled by the base class.

### Subcommands

Two patterns ship in the template:

1. **Nested `AsyncCommand`s** via `addSubCommand(...)` — see how `AsyncCommand.onCommand` dispatches the first arg.
2. **`SubCommandRegistry<T>`** — see `command/example/test/TestCommand.kt`. Register `SubCommand<T>` instances and the registry handles dispatch, tab completion, permission filtering, and `/<cmd>` help output.

## Adding a listener

Extend `BaseListener` — registration with the plugin manager happens in its `init` block. Just instantiate it from `onEnable`:

```kotlin
PlayerListener(this)
```

## Database

Disabled by default. Flip it on in `config.yml`:

```yaml
database:
  enabled: true
  jdbcUrl: "jdbc:postgresql://localhost:5432/mydb"
  username: "postgres"
  password: "..."
  pool:
    maximumPoolSize: 10
    minimumIdle: 2
    connectionTimeoutMs: 10000
    idleTimeoutMs: 600000
    maxLifetimeMs: 1800000
```

Then use the manager from your code:

```kotlin
plugin.databaseManager.executeAsync { conn ->
    conn.prepareStatement("INSERT INTO foo (bar) VALUES (?)").use { ps ->
        ps.setString(1, "baz")
        ps.executeUpdate()
    }
}

plugin.databaseManager.queryAsync { conn ->
    conn.prepareStatement("SELECT count(*) FROM foo").use { ps ->
        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }
}.thenAccept { count -> plugin.logger.info("rows: $count") }
```

The pool and its executor are torn down in `onDisable`.

## External config files

```kotlin
val messages = ExternalConfig("messages.yml", this) // copies from resources if missing
val prefix = messages.configuration.getString("prefix")
messages.reload()
messages.save()
```

## License

[MIT](LICENSE) — free for everyone to use, modify, and distribute.
