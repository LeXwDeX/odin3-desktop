package com.odin.desktop.shader.runtime

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.odin.desktop.shader.model.ShaderFamily

data class ShaderRuntimeSelection(
    val family: ShaderFamily,
    val status: String,
    val hasTarget: Boolean
)

/** Select an available integration, never infer a running graphics API from an app's name. */
object ShaderRuntime {
    fun resolve(context: Context, packageName: String?): ShaderRuntimeSelection {
        val hasTarget = !packageName.isNullOrBlank() && packageName != context.packageName &&
            packageName != "com.android.systemui" && packageName != "android" &&
            runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }
                .getOrDefault(false)

        // No native Vulkan/GLES hook is attached in this build. The available live integration
        // is the AGSL multiplicative mask, which implements GameNative's Vulkan CRT equations.
        // This is a capability fallback, not a claim that the target app itself uses Vulkan.
        val status = when {
            !hasTarget -> "请先打开游戏，再从下拉面板选择滤镜"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Settings.canDrawOverlays(context) ->
                "自动 · 兼容扫描线"
            else -> "自动 · 截图预览"
        }
        return ShaderRuntimeSelection(ShaderFamily.VULKAN, status, hasTarget)
    }
}
