package com.odin.desktop.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val isGameTab: Boolean = false, // 标记是否为游戏分类（用于风扇调度判定）
    val iconKey: String? = null
)

enum class TabAction {
    MOVE_UP,
    MOVE_DOWN,
    SET_DEFAULT,
    DELETE
}

fun getAvailableTabActions(tab: TabEntity, index: Int, totalTabs: Int): List<TabAction> {
    val list = mutableListOf<TabAction>()
    if (index > 0) list.add(TabAction.MOVE_UP)
    if (index < totalTabs - 1) list.add(TabAction.MOVE_DOWN)
    if (!tab.isDefault) list.add(TabAction.SET_DEFAULT)
    if (!tab.isDefault && tab.name != "全部应用") list.add(TabAction.DELETE)
    return list
}
