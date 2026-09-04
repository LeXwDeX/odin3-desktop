package com.odin.desktop.dashboard

data class StorageUsage(
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
    val systemBytes: Long? = null,
    val appsBytes: Long? = null,
    val otherBytes: Long? = null,
    val loading: Boolean = true,
    val needsUsageAccess: Boolean = false,
    val note: String? = null
)

data class ExternalStorageUsage(
    val id: String,
    val label: String,
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
    val readOnly: Boolean = false,
    val note: String? = null
)

data class MemoryUsage(
    val totalBytes: Long? = null,
    val usedBytes: Long? = null,
    val nonSystemAppBytes: Long? = null,
    val note: String? = null
)

data class ProcessorUsage(
    val temperatureC: Float? = null,
    val note: String? = null
)

data class WifiUsage(
    val connected: Boolean = false,
    val ssid: String? = null,
    val rxBytesPerSecond: Long? = null,
    val txBytesPerSecond: Long? = null,
    val needsLocationAccess: Boolean = false,
    val note: String? = null
)

data class DashboardState(
    val storage: StorageUsage = StorageUsage(),
    val externalStorage: List<ExternalStorageUsage> = emptyList(),
    val memory: MemoryUsage = MemoryUsage(),
    val cpu: ProcessorUsage = ProcessorUsage(),
    val gpu: ProcessorUsage = ProcessorUsage(),
    val wifi: WifiUsage = WifiUsage(),
    val loading: Boolean = true,
    val updatedAtMillis: Long = 0L
)

/** Only the four explicit actions participate in dashboard selection. */
enum class DashboardAction {
    FILES, SYSTEM_SETTINGS, ODIN_SETTINGS, FILTERS
}
