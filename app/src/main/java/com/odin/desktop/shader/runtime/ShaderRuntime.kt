package com.odin.desktop.shader.runtime

import com.odin.desktop.R
import android.content.Context
import android.os.Build
import android.provider.Settings

data class ShaderRuntimeSelection(
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
            !hasTarget -> context.getString(R.string.text_open_a_game_then_choose_the_filter)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Settings.canDrawOverlays(context) ->
                context.getString(R.string.text_auto_compatible_scanlines)
            else -> context.getString(R.string.text_auto_screenshot_preview)
        }
        return ShaderRuntimeSelection(status, hasTarget)
    }
}
