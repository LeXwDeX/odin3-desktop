package com.odin.desktop.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.odin.desktop.service.fan.HardwareController
import com.odin.desktop.ui.navigation.GamepadKeyHandler
import com.odin.desktop.ui.screens.LauncherScreen
import com.odin.desktop.ui.theme.OdinDesktopTheme
import com.odin.desktop.ui.viewmodel.LauncherViewModel

/**
 * Odin 3 专属掌机桌面启动台（MainActivity）。
 * 注册为系统 Default Home Launcher。
 * 具备全屏沉浸式 OLED 纯黑布局与全局实体手柄按键路由。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            viewModel.scanInstalledApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册应用安装与卸载全局广播监听
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)

        // 默认应用传感器横屏
        HardwareController.applyOrientation(this, HardwareController.ORIENTATION_SENSOR_LANDSCAPE)

        // 全屏沉浸模式（隐藏系统导航条与状态栏，滑动临时浮现）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            OdinDesktopTheme {
                LauncherScreen(
                    viewModel = viewModel,
                    onOrientationChange = { mode ->
                        HardwareController.applyOrientation(this@MainActivity, mode)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val fanIntent = android.content.Intent(this, com.odin.desktop.service.fan.FanWatchdogService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(fanIntent)
            } else {
                startService(fanIntent)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // 回到桌面时刷新硬件状态与应用列表，并确保隐藏 VideoShader 遮罩 (Shader 仅在应用内生效)
        viewModel.loadHardwareStates()
        viewModel.scanInstalledApps()
        com.odin.desktop.shader.engine.VideoShaderEngine.onForegroundPackageChanged(this, packageName)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {}
    }

    /**
     * 实体手柄全局按键拦截。
     * 拦截 D-Pad、摇杆、肩键 (L1/R1) 与 ABXY，实现完全不需要触屏的掌机盲操。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadKeyHandler.handleKeyEvent(event, viewModel)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
