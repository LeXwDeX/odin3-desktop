package com.odin.desktop.data.model

import android.content.Context
import com.odin.desktop.R
import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind

fun TabEntity.displayName(context: Context): String {
    if (!usesDefaultName) return name
    val label = when (kind) {
        TabKind.GAMES -> R.string.tab_games
        TabKind.SYSTEM -> R.string.tab_system
        TabKind.ALL_APPS -> R.string.tab_all_apps
        else -> return name
    }
    return context.getString(label)
}
