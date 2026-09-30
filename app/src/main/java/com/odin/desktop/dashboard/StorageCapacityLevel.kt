package com.odin.desktop.dashboard

internal enum class StorageCapacityLevel { NORMAL, LOW, CRITICAL }

/** Compare byte counts without rounded percentages or overflowing large capacities. */
internal fun storageCapacityLevel(freeBytes: Long?, totalBytes: Long?): StorageCapacityLevel {
    if (freeBytes == null || freeBytes < 0 || totalBytes == null || totalBytes <= 0) {
        return StorageCapacityLevel.NORMAL
    }
    fun threshold(percent: Long) = totalBytes / 100 * percent + totalBytes % 100 * percent / 100
    return when {
        freeBytes <= threshold(15) -> StorageCapacityLevel.CRITICAL
        freeBytes <= threshold(20) -> StorageCapacityLevel.LOW
        else -> StorageCapacityLevel.NORMAL
    }
}
