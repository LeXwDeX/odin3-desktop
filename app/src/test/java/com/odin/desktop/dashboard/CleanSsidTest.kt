package com.odin.desktop.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanSsidTest {
    @Test fun hidesUnknownAndEmptyValuesFromWifiState() {
        assertNull(cleanSsid(null))
        assertNull(cleanSsid(""))
        assertNull(cleanSsid("\"\""))
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid("\"<unknown ssid>\""))
        assertNull(cleanSsid("0x"))
    }

    @Test fun keepsNamedSsidAndRemovesFrameworkQuotes() {
        assertEquals("Odin Wi-Fi", cleanSsid("\"Odin Wi-Fi\""))
        assertEquals("Odin Wi-Fi", cleanSsid("Odin Wi-Fi"))
    }
}
