package com.odin.desktop.ui

import com.odin.desktop.data.entity.TabEntity
import com.odin.desktop.data.entity.TabKind
import com.odin.desktop.ui.components.AppActionType
import com.odin.desktop.ui.components.getAvailableAppActions
import org.junit.Assert.assertEquals
import org.junit.Test

class AppActionAvailabilityTest {
    private val all = TabEntity(id = 1, name = "All apps", kind = TabKind.ALL_APPS)
    private val games = TabEntity(id = 2, name = "My games")
    private val tools = TabEntity(id = 3, name = "Tools")

    @Test fun allAppsCannotRemoveAnInstalledApplicationFromItsOwnCollection() {
        assertEquals(listOf(AppActionType.MOVE_TO_TAB, AppActionType.APP_DETAILS),
            getAvailableAppActions(all, listOf(all, games)))
    }

    @Test fun noDestinationLeavesDetailsAsTheFirstFocusableAction() {
        assertEquals(listOf(AppActionType.APP_DETAILS), getAvailableAppActions(all, listOf(all)))
        assertEquals(listOf(AppActionType.APP_DETAILS, AppActionType.REMOVE_ICON),
            getAvailableAppActions(games, listOf(all, games)))
    }

    @Test fun customCategoryCanMoveToAnotherCustomCategory() {
        assertEquals(AppActionType.entries.toList(), getAvailableAppActions(games, listOf(all, games, tools)))
    }
}
