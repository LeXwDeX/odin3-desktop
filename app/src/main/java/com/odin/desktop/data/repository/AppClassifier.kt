package com.odin.desktop.data.repository

import com.odin.desktop.data.model.InstalledApp

/** Built-in adapters can replace package classification without changing tab persistence. */
interface AppClassifier {
    fun isGame(app: InstalledApp): Boolean
    fun isSystem(app: InstalledApp): Boolean
}

object AndroidAppClassifier : AppClassifier {
    private val emulatorHints = listOf(
        "emu", "mame", "duckstation", "ppsspp", "retroarch", "armsx2", "es_de", "citron", "yuzu"
    )

    override fun isGame(app: InstalledApp): Boolean =
        app.isGame || emulatorHints.any { app.packageName.contains(it, ignoreCase = true) }

    override fun isSystem(app: InstalledApp): Boolean = app.isSystemApp ||
        app.packageName.startsWith("com.google.android.") ||
        app.packageName.startsWith("com.android.") || app.packageName == "com.odin.settings"
}
