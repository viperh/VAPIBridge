package me.qviperh.vAPIBridge.metrics

import me.qviperh.vAPIBridge.dto.Platform


object Platforms {
    val current: Platform by lazy {
        runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer")}
            .fold({Platform.FOLIA}, {Platform.PAPER})
    }
    val isFolia get() = current == Platform.FOLIA
}


internal fun Double.r2(): Double = Math.round(this * 100) / 100.0