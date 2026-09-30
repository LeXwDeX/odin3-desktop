package com.odin.desktop.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageCapacityLevelTest {
    @Test fun thresholdsIncludeFifteenAndTwentyPercent() {
        assertEquals(StorageCapacityLevel.CRITICAL, storageCapacityLevel(0, 100))
        assertEquals(StorageCapacityLevel.CRITICAL, storageCapacityLevel(15, 100))
        assertEquals(StorageCapacityLevel.LOW, storageCapacityLevel(16, 100))
        assertEquals(StorageCapacityLevel.LOW, storageCapacityLevel(20, 100))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(21, 100))
    }

    @Test fun comparesExactBytesAtFractionalAndLargeThresholds() {
        assertEquals(StorageCapacityLevel.CRITICAL, storageCapacityLevel(15, 101))
        assertEquals(StorageCapacityLevel.LOW, storageCapacityLevel(16, 101))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(21, 101))
        val total = Long.MAX_VALUE
        val twentyPercent = total / 100 * 20 + total % 100 * 20 / 100
        assertEquals(StorageCapacityLevel.LOW, storageCapacityLevel(twentyPercent, total))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(twentyPercent + 1, total))
    }

    @Test fun unavailableAndInvalidCapacityDoNotWarn() {
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(null, 100))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(-1, 100))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(0, null))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(0, 0))
        assertEquals(StorageCapacityLevel.NORMAL, storageCapacityLevel(0, -1))
    }
}
