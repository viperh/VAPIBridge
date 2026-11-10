package me.qviperh.vAPIBridge.metrics

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.qviperh.vAPIBridge.dto.CpuDto
import me.qviperh.vAPIBridge.dto.GcDto
import me.qviperh.vAPIBridge.dto.MemoryDto
import me.qviperh.vAPIBridge.dto.MsptDto
import me.qviperh.vAPIBridge.dto.PerformanceDto
import me.qviperh.vAPIBridge.dto.RegionSampleDto
import me.qviperh.vAPIBridge.dto.RegionSummaryDto
import me.qviperh.vAPIBridge.dto.ThreadsDto
import me.qviperh.vAPIBridge.dto.TickRateDto
import me.qviperh.vAPIBridge.dto.TpsDto
import me.qviperh.vAPIBridge.dto.WorldLoadDto
import me.qviperh.vAPIBridge.onGlobal
import me.qviperh.vAPIBridge.onRegion
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import org.bukkit.Location
import java.lang.management.ManagementFactory

class PerformanceCollector(private val plugin: Plugin, private val maxRegionSamples: Int = 32) {

    private val regionTpsMethod: Method? by lazy {
        runCatching {
            plugin.server.javaClass.getMethod("getRegionTPS", Location::class.java) }.getOrNull()
    }


    suspend fun collect(): PerformanceDto {
        val paperTick = if (Platforms.isFolia) null else plugin.onGlobal { paperTickStats() }
        val worlds = plugin.onGlobal { worldLoad() }
        val regions = if (Platforms.isFolia) regionSummary() else null
        val rt = ManagementFactory.getRuntimeMXBean()
        return PerformanceDto(
            platform = Platforms.current,
            timestamp = System.currentTimeMillis(),
            tps = paperTick?.first,
            mspt = paperTick?.second,
            tickRate = paperTick?.third,
            regions = regions,
            worlds = worlds,
            memory = memory(),
            cpu = cpu(),
            threads = threads(),
            gc = gc(),
            uptimeMillis = rt.uptime
        )
    }



    private fun paperTickStats(): Triple<TpsDto, MsptDto, TickRateDto?> {
        val s = plugin.server
        val tps = s.tps.map { it.coerceAtMost(20.0).r2() }
        val ticks = s.tickTimes.filter { it > 0 }.map { it / 1_000_000.0 }.sorted()
        val mspt = if (ticks.isEmpty()) {
            MsptDto(0.0, 0.0, 0.0, 0.0, 0)
        } else {
            MsptDto(
                avg = ticks.average().r2(),
                min = ticks.first().r2(),
                max = ticks.last().r2(),
                p95 = ticks[((ticks.size - 1) * 0.95).toInt()].r2(),
                samples = ticks.size
            )
        }
        val tickRate = runCatching {
            val m = s.serverTickManager
            TickRateDto(m.tickRate.toDouble().r2(), m.isFrozen, m.isSprinting)
        }.getOrNull()
        return Triple(TpsDto(tps[0], tps[1], tps[2]), mspt, tickRate)
    }

    private fun worldLoad(): List<WorldLoadDto> = plugin.server.worlds.map { w ->
        WorldLoadDto(
            name = w.name,
            players = w.playerCount,
            loadedChunks = if (Platforms.isFolia) null else runCatching { w.chunkCount }.getOrNull(),
            entities = if (Platforms.isFolia) null else runCatching { w.entityCount }.getOrNull(),
            tileEntities = if (Platforms.isFolia) null else runCatching { w.tileEntityCount }.getOrNull()
        )
    }

    private suspend fun regionSummary(): RegionSummaryDto? {
        val method = regionTpsMethod ?: return null
        val anchors = plugin.onGlobal {
            plugin.server.onlinePlayers
                .groupBy { Triple(it.world.name, it.location.blockX shr 4 shr 3, it.location.blockZ shr 4 shr 3) }
                .values
                .take(maxRegionSamples)
                .map { group -> group.first().location to group.size }
        }
        if (anchors.isEmpty()) return null
        val samples = coroutineScope {
            anchors.map { (loc, count) ->
                async {
                    plugin.onRegion(loc) {
                        val values = method.invoke(plugin.server, loc) as? DoubleArray ?: return@onRegion null
                        RegionSampleDto(
                            world = loc.world.name,
                            chunkX = loc.blockX shr 4,
                            chunkZ = loc.blockZ shr 4,
                            tps5s = values.getOrElse(0) { 0.0 }.r2(),
                            tps1m = values.getOrElse(2) { values.last() }.r2(),
                            players = count
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (samples.isEmpty()) return null
        val tps = samples.map { it.tps5s }
        return RegionSummaryDto(samples.size, tps.min(), tps.average().r2(), tps.max(), samples.sortedBy { it.tps5s })
    }

    private fun memory(): MemoryDto {
        val r = Runtime.getRuntime()
        val mb = 1024 * 1024
        val allocated = r.totalMemory()
        val free = r.freeMemory()
        val used = allocated - free
        val max = r.maxMemory()
        return MemoryDto(used / mb, allocated / mb, max / mb, (max - used) / mb, (used.toDouble() / max * 100).r2())
    }

    private fun cpu(): CpuDto {
        val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        fun Double.pct() = takeIf { it >= 0 }?.let { (it * 100).r2() }
        return CpuDto(
            process = os?.processCpuLoad?.pct(),
            system = os?.cpuLoad?.pct(),
            cores = Runtime.getRuntime().availableProcessors()
        )
    }

    private fun threads(): ThreadsDto {
        val t = ManagementFactory.getThreadMXBean()
        return ThreadsDto(t.threadCount, t.daemonThreadCount, t.peakThreadCount)
    }

    private fun gc(): List<GcDto> = ManagementFactory.getGarbageCollectorMXBeans().map {
        GcDto(it.name, it.collectionCount, it.collectionTime)
    }
}