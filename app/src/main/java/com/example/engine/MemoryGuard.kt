package com.example.engine

import android.app.ActivityManager
import android.content.Context

data class MemoryStatus(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val appHeapUsedMb: Long,
    val appHeapMaxMb: Long,
    val isLowRamDevice: Boolean,
    val lowMemoryPressure: Boolean,
)

object MemoryGuard {

    fun inspect(context: Context): MemoryStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val heapMaxMb = runtime.maxMemory() / (1024L * 1024L)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val isLowRam = (am?.isLowRamDevice == true) || (totalRamMb in 1..1350)

        return MemoryStatus(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            appHeapUsedMb = heapUsedMb,
            appHeapMaxMb = heapMaxMb,
            isLowRamDevice = isLowRam,
            lowMemoryPressure = memInfo.lowMemory || availRamMb in 1..180,
        )
    }
}
