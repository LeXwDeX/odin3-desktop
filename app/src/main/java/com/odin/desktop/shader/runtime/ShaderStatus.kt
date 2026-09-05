package com.odin.desktop.shader.runtime

import com.odin.desktop.R
import com.odin.desktop.shader.model.GameNativeShaderSettings

/** Evidence about this target, never a persisted preference or screenshot-preview result. */
enum class ShaderStatus(val message: Int) {
    NO_TARGET(R.string.shader_status_no_target),
    DISABLED(R.string.shader_status_disabled),
    CHECKING(R.string.shader_status_checking),
    PREVIEW_ONLY(R.string.shader_status_preview_only),
    NO_EFFECT(R.string.shader_status_no_effect),
    PERMISSION_REQUIRED(R.string.shader_status_permission),
    UNSUPPORTED_SYSTEM(R.string.shader_status_system),
    PAUSED(R.string.shader_status_paused),
    OVERLAY_UNCONFIRMED(R.string.shader_status_overlay),
    UNKNOWN(R.string.shader_status_unknown),
    FAILED(R.string.shader_status_failed);

    companion object {
        fun evaluate(enabled: Boolean, effects: GameNativeShaderSettings, sdk: Int,
                     permission: Boolean, foregroundKnown: Boolean, paused: Boolean): ShaderStatus = when {
            !enabled -> DISABLED
            effects.requiresFrameInput -> PREVIEW_ONLY
            !effects.enableCRT -> NO_EFFECT
            sdk < 33 -> UNSUPPORTED_SYSTEM
            !permission -> PERMISSION_REQUIRED
            !foregroundKnown -> UNKNOWN
            paused -> PAUSED
            else -> CHECKING
        }
    }
}

data class ShaderRuntimeState(
    val packageName: String? = null,
    val requestedEnabled: Boolean? = null,
    val status: ShaderStatus = ShaderStatus.NO_TARGET
)
