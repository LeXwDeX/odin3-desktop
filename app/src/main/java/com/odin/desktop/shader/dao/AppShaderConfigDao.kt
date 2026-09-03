package com.odin.desktop.shader.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.odin.desktop.shader.model.AppShaderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppShaderConfigDao {

    @Query("SELECT * FROM app_shader_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getConfig(packageName: String): AppShaderConfigEntity?

    @Query("SELECT * FROM app_shader_configs WHERE packageName = :packageName LIMIT 1")
    fun getConfigFlow(packageName: String): Flow<AppShaderConfigEntity?>

    @Query("SELECT * FROM app_shader_configs WHERE isEnabled = 1")
    suspend fun getAllEnabledConfigs(): List<AppShaderConfigEntity>

    @Query("SELECT * FROM app_shader_configs WHERE isEnabled = 1")
    fun getAllEnabledConfigsFlow(): Flow<List<AppShaderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: AppShaderConfigEntity)

    @Query("DELETE FROM app_shader_configs WHERE packageName = :packageName")
    suspend fun deleteConfig(packageName: String)
}
