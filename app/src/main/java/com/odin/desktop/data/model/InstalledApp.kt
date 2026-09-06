package com.odin.desktop.data.model

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val isGame: Boolean = false,
    val firstInstallTime: Long = 0L
)

/** New installations precede the saved manual order, independent of installer or label. */
fun orderAllApps(apps: List<InstalledApp>, savedPackages: List<String>): List<InstalledApp> {
    val byPackage = apps.associateBy { it.packageName }
    val saved = savedPackages.distinct().mapNotNull(byPackage::get)
    val savedSet = savedPackages.toSet()
    val additions = byPackage.values.filter { it.packageName !in savedSet }
        .sortedWith(compareByDescending<InstalledApp> { it.firstInstallTime }
            .thenBy { it.label }.thenBy { it.packageName })
    return additions + saved
}
