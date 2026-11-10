package me.qviperh.vAPIBridge.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

@Serializable
enum class Platform {
    @SerialName("paper") PAPER,
    @SerialName("folia") FOLIA,
}

@Serializable
data class ServerDto(
    val name: String,
    val platform: Platform,
    val version: String,
    val bukkitVersion: String,
    val minecraftVersion: String,
    val motd: String,
    val motdPlain: String,
    val plugin: PluginInfoDto,
    val players: PlayerCountDto,
    val network: NetworkDto,
    val settings: ServerSettingsDto,
    val worlds: List<String>,
    val runtime: RuntimeInfoDto,
)

@Serializable
data class PluginInfoDto(
    val name: String,
    val mainClass: String,
    val version: String,
    val apiVersion: String? = "unknown",
)
@Serializable
data class PlayerCountDto(
    val online: Int,
    val max: Int
)
@Serializable
data class NetworkDto(
    val ip: String,
    val port: Int,
    val onlineMode: Boolean,
)
@Serializable
data class ServerSettingsDto(
    val defaultGameMode: String,
    val hardcore: Boolean,
    val whitelist: Boolean,
    val whitelistEnforced: Boolean,
    val viewDistance: Int,
    val simulationDistance: Int,
    val allowNether: Boolean,
    val allowEnd: Boolean,
)
@Serializable
data class RuntimeInfoDto(
    val javaVersion: String,
    val javaVendor: String,
    val os: String,
    val arch: String,
    val availableProcessors: Int,
    val startedAt: Long,
    val uptimeMillis: Long
)


@Serializable
data class MotdDto(
    val motd: String
)

@Serializable
data class LocationDto(
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
)

@Serializable
data class PerformanceDto(
    val platform: Platform,
    val timestamp: Long,
    val tps: TpsDto? = null,
    val mspt: MsptDto? = null,
    val tickRate: TickRateDto? = null,
    val regions: RegionSummaryDto? = null,
    val worlds: List<WorldLoadDto>,
    val memory: MemoryDto,
    val cpu: CpuDto,
    val threads: ThreadsDto,
    val gc: List<GcDto>,
    val uptimeMillis: Long
)

@Serializable
data class TpsDto(
    val last1m: Double,
    val last5m: Double,
    val last15m: Double,
)

@Serializable
data class MsptDto(
    val avg: Double,
    val min: Double,
    val max:Double,
    val p95: Double,
    val samples: Int,
)

@Serializable
data class TickRateDto(
    val target: Double,
    val frozen: Boolean,
    val sprinting: Boolean
)

@Serializable
data class RegionSummaryDto(
    val sampled: Int,
    val minTps: Double,
    val avgTps: Double,
    val maxTps: Double,
    val samples: List<RegionSampleDto>
)

@Serializable
data class RegionSampleDto(
    val world: String,
    val chunkX: Int,
    val chunkZ: Int,
    val tps5s: Double,
    val tps1m: Double,
    val players: Int
)

@Serializable
data class MemoryDto(
    val usedMb: Long,
    val allocatedMb: Long,
    val maxMb: Long,
    val freeMb: Long,
    val usagePercent: Double,
)

@Serializable
data class BlockDto(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)


@Serializable
data class CpuDto(
    val process: Double? = null,
    val system: Double? = null,
    val cores: Int,
)

@Serializable
data class ThreadsDto(
    val live: Int,
    val daemon: Int,
    val peak: Int
)

@Serializable
data class WorldLoadDto(
    val name: String,
    val players: Int,
    val loadedChunks: Int? = null,
    val entities: Int? = null,
    val tileEntities: Int? = null,
)

@Serializable
data class GcDto(
    val name: String,
    val collections: Long,
    val timeMillis: Long,
)

@Serializable
data class ItemStackDto(
    val type: String,
    val amount: Int,
    val name: String,
    val lore: List<String>,
    val enchantments: List<Enchantment>
)

@Serializable
data class AreaDto(
    val x: Double,
    val y: Double,
    val z: Double,
    val radius: Double
)

data class CuboidDto(
    val world: String,
    val from: BlockDto,
    val to: BlockDto,
)

@Serializable
data class Enchantment(
    val name: String,
    val amount: Int,
)

data class EffectDto(
    val type: String,
    val duration: Int,
    val amplifier: Int,
    val ambient: Boolean,
    val particles: Boolean,
    val icon: Boolean,
)
@Serializable
data class CommandReqDto(
    val command: String,
    val captureOutput: Boolean = true,
    val waitMillis: Long = 100
)

@Serializable
data class SaveReqDto(
    val flush: Boolean,
)
@Serializable
data class SaveResDto(
    val success: Boolean,
    val flushed: Boolean,
    val output: List<String> = emptyList(),
    val error: String? = null,
    val durationMillis: Long? = null
)

@Serializable
data class ShutdownDto(
    val delaySeconds: Long? = null,
    val message: String? = null,
)
@Serializable
data class PluginsDto(
    val plugins: List<PluginInfoDto>
)

@Serializable
data class BasicResponseDto(
    val message: String,
    val error: String? = null,
)

@Serializable
data class CommandResDto(
    val command: String,
    val success: Boolean,
    val output: List<String> = emptyList(),
    val error: String? = null,
    val durationMillis: Long?

)

@Serializable
data class PlayerDto(
    val uuid: String,
    val isOnline: Boolean,
    val name: String? = null,
    val gameMode: String? = null,
    val health: Double? = null,
    val foodLevel: Int? = null,
    val saturation: Float? = null,
    val level: Int? = null,
    val exp: Float? = null,
    val allowFlight: Boolean? = null,
    val flying: Boolean? = null,
    val walkSpeed: Float? = null,
    val flySpeed: Float? = null,
    val displayName: String? = null,
    val location: LocationDto? = null
) {

    companion object {
        fun fromPlayer(player: Player?): PlayerDto {
            return PlayerDto(
                player?.uniqueId.toString(),
                player?.isOnline ?: false,
                player?.name,
                player?.gameMode?.name,
                player?.health,
                player?.foodLevel,
                player?.saturation,
                player?.level,
                player?.exp,
                player?.allowFlight,
                player?.isFlying,
                player?.walkSpeed,
                player?.flySpeed,
                player?.displayName().toString(),
                LocationDto(
                    player?.location?.world?.name,
                    player?.location?.x,
                    player?.location?.y,
                    player?.location?.z,
                    player?.location?.yaw,
                    player?.location?.pitch
                )
            )
        }


        fun fromOfflinePlayer(oplayer: OfflinePlayer): PlayerDto {
            if (oplayer.player != null) return fromPlayer(oplayer.player)

            return PlayerDto(
                oplayer.uniqueId.toString(),
                oplayer.isOnline ?: false,
                oplayer.name,
                oplayer.player?.gameMode?.name,
                oplayer.player?.health,
                oplayer.player?.foodLevel,
                oplayer.player?.saturation,
                oplayer.player?.level,
                oplayer.player?.exp,
                oplayer.player?.allowFlight,
                oplayer.player?.isFlying,
                oplayer.player?.walkSpeed,
                oplayer.player?.flySpeed,
                oplayer.player?.displayName().toString(),
                LocationDto(
                    oplayer.location?.world?.name,
                    oplayer.location?.x,
                    oplayer.location?.y,
                    oplayer.location?.z,
                    oplayer.location?.yaw,
                    oplayer.location?.pitch
                )
            )

        }
    }
}




@Serializable
data class MessageDto(val text: String)

@Serializable
data class BroadcastDto(val text: String)
