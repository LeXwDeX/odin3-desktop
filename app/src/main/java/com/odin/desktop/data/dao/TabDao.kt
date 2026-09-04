package com.odin.desktop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.odin.desktop.data.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY sortOrder ASC, id ASC")
    fun getAllTabsFlow(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllTabs(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: Long): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>): List<Long>

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Update
    suspend fun updateTabs(tabs: List<TabEntity>)

    @Query("UPDATE tabs SET isDefault = CASE WHEN id = :defaultTabId THEN 1 ELSE 0 END WHERE EXISTS (SELECT 1 FROM tabs WHERE id = :defaultTabId)")
    suspend fun setDefaultTab(defaultTabId: Long)

    @Query("DELETE FROM tabs WHERE id = :tabId AND isDefault = 0 AND kind != 'all_apps'")
    suspend fun deleteTabById(tabId: Long)

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun getTabCount(): Int
}
