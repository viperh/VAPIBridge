package me.qviperh.vAPIBridge

import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

suspend fun <T> Plugin.onGlobal(block: () -> T): T = suspendCancellableCoroutine { cont ->
    server.globalRegionScheduler.execute(this) { cont.resumeWith(runCatching(block)) }
}

suspend fun <T> Plugin.onRegion(location: Location, block: () -> T): T = suspendCancellableCoroutine { cont ->
    server.regionScheduler.execute(this, location) { cont.resumeWith(runCatching(block)) }
}

suspend fun <T> Plugin.onEntity(entity: Entity, block: () -> T): T = suspendCancellableCoroutine { cont ->
    val task = entity.scheduler.run(this, { cont.resumeWith(runCatching(block)) }) {
        cont.resumeWith(Result.failure(IllegalStateException("Entity was removed")))
    }
    if (task == null) cont.resumeWith(Result.failure(IllegalStateException("Entity was removed")))
}