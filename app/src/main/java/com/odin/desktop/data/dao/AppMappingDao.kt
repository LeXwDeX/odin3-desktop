package com.odin.desktop.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.odin.desktop.data.entity.AppMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMappingDao {
    @Query("SELECT * FROM app_mappings WHERE tabId = :tabId AND isHidden = 0 ORDER BY sortOrder ASC, id ASC")
    fun getAppsForTabFlow(tabId: Long): Flow<List<AppMappingEntity>>

    @Query("SELECT * FROM app_mappings WHERE tabId = :tabId ORDER BY sortOrder ASC, id ASC")
    suspend fun getAppsForTab(tabId: Long): List<AppMappingEntity>

    @Query("SELECT packageName FROM app_mappings WHERE tabId IN (SELECT id FROM tabs WHERE isGameTab = 1)")
    fun getGamePackageNamesFlow(): Flow<List<String>>

    @Query("SELECT packageName FROM app_mappings WHERE tabId IN (SELECT id FROM tabs WHERE isGameTab = 1)")
    suspend fun getGamePackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: AppMappingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<AppMappingEntity>)

    @Update
    suspend fun updateMapping(mapping: AppMappingEntity)

    @Delete
    suspend fun deleteMapping(mapping: AppMappingEntity)

    @Query("DELETE FROM app_mappings WHERE tabId = :tabId AND packageName = :packageName")
    suspend fun removeAppFromTab(tabId: Long, packageName: String)

    @Query("DELETE FROM app_mappings WHERE packageName = :packageName")
    suspend fun removeAppFromAllTabs(packageName: String)
}
