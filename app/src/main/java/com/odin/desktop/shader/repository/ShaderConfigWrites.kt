package com.odin.desktop.shader.repository

import com.odin.desktop.R
import android.content.Context
import com.odin.desktop.data.db.OdinDatabase
import com.odin.desktop.shader.engine.VideoShaderEngine
import com.odin.desktop.shader.model.AppShaderConfigEntity
import com.odin.desktop.shader.model.GameNativeShaderSettings
import kotlinx.coroutines.*

/** 异步持久化配置队列 */
internal object ShaderConfigWrites {
    private const val SCREENSHOT_PREFERENCES = "shader_screenshot_preview"
    private const val SCREENSHOT_EFFECTS = "effects_json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tail: Deferred<Result<Unit>> = CompletableDeferred(Result.success(Unit))

    @Synchronized
    fun save(context: Context, value: AppShaderConfigEntity): Deferred<Result<Unit>> {
        val app = context.applicationContext
        return enqueue {
            OdinDatabase.getDatabase(app).appShaderConfigDao().insertOrUpdate(value)
            withContext(Dispatchers.Main.immediate) { VideoShaderEngine.refreshConfig(app, value.packageName) }
        }
    }

    suspend fun loadScreenshotSettings(context: Context): GameNativeShaderSettings = withContext(Dispatchers.IO) {
        val json = context.applicationContext.getSharedPreferences(SCREENSHOT_PREFERENCES, Context.MODE_PRIVATE)
            .getString(SCREENSHOT_EFFECTS, "").orEmpty()
        GameNativeShaderSettings.fromJson(json)
    }

    fun saveScreenshotSettings(context: Context, value: GameNativeShaderSettings): Deferred<Result<Unit>> {
        val app = context.applicationContext
        return enqueue {
            check(app.getSharedPreferences(SCREENSHOT_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(SCREENSHOT_EFFECTS, value.toJson()).commit()) { context.getString(R.string.text_could_not_save_screenshot_preview_settings) }
        }
    }

    @Synchronized
    private fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val previous = tail
        return scope.async {
            previous.await()
            runCatching { write() }
        }.also { tail = it }
    }
}
