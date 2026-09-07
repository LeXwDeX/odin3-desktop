package com.odin.desktop.data.model

import java.text.Collator
import java.util.Locale

const val HOME_APP_LIMIT = 20

enum class AppSortMode { MANUAL, INSTALLED, LAST_USED, NAME }

fun sortApps(apps: List<InstalledApp>, mode: AppSortMode, locale: Locale = Locale.getDefault()): List<InstalledApp> {
    if (mode == AppSortMode.MANUAL) return apps
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val names = Comparator<InstalledApp> { a, b -> collator.compare(a.label, b.label) }
        .thenBy { it.packageName }
    return apps.sortedWith(when (mode) {
        AppSortMode.INSTALLED -> compareByDescending<InstalledApp> { it.firstInstallTime }.then(names)
        AppSortMode.LAST_USED -> compareByDescending<InstalledApp> { it.lastTimeUsed ?: 0L }.then(names)
        else -> names
    })
}

fun moveApp(apps: List<InstalledApp>, from: Int, to: Int): List<InstalledApp> {
    if (from !in apps.indices || to !in apps.indices || from == to) return apps
    return apps.toMutableList().apply { add(to, removeAt(from)) }
}

fun homeAppCount(total: Int, expanded: Boolean): Int =
    if (expanded) total else total.coerceAtMost(HOME_APP_LIMIT) + if (total > HOME_APP_LIMIT) 1 else 0
