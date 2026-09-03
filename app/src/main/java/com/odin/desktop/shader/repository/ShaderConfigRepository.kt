package com.odin.desktop.shader.repository

import com.odin.desktop.shader.dao.AppShaderConfigDao
import com.odin.desktop.shader.model.AppShaderConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ShaderConfigRepository(
    private val shaderDao: AppShaderConfigDao
) {
    fun getConfigFlow(packageName: String): Flow<AppShaderConfigEntity?> {
        return shaderDao.getConfigFlow(packageName)
    }

    suspend fun getConfig(packageName: String): AppShaderConfigEntity? = withContext(Dispatchers.IO) {
        shaderDao.getConfig(packageName)
    }

    suspend fun saveConfig(config: AppShaderConfigEntity) = withContext(Dispatchers.IO) {
        shaderDao.insertOrUpdate(config)
    }

    suspend fun toggleShaderEnabled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val current = shaderDao.getConfig(packageName) ?: AppShaderConfigEntity.defaultFor(packageName)
        val updated = current.copy(isEnabled = !current.isEnabled)
        shaderDao.insertOrUpdate(updated)
        updated.isEnabled
    }
}
