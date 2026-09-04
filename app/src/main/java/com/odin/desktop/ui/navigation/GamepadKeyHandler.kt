package com.odin.desktop.ui.navigation

import android.view.KeyEvent
import com.odin.desktop.ui.viewmodel.LauncherViewModel

object GamepadKeyHandler {

    private var xKeyDownTime = 0L
    private var yKeyDownTime = 0L
    private const val LONG_PRESS_THRESHOLD_MS = 300L

    fun handleKeyEvent(event: KeyEvent, viewModel: LauncherViewModel): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_X && viewModel.focusZone.value == FocusZone.DOCK) {
            val dockIndex = viewModel.selectedDockIndex.value
            if (dockIndex == 1) {
                xKeyDownTime = 0L
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) viewModel.toggleAutoFanControl()
                return true
            } else if (dockIndex == 3) {
                xKeyDownTime = 0L
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) viewModel.toggleChargingSeparation()
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // 肩键 Tab 轮播
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (event.repeatCount == 0) viewModel.onPrevTab()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (event.repeatCount == 0) viewModel.onNextTab()
                    return true
                }

                // 方向键 / 摇杆 D-Pad
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.onNavigateLeft()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.onNavigateRight()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.onNavigateUp()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.onNavigateDown()
                    return true
                }

                // 实体 A 键 / 确定
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.repeatCount == 0) viewModel.onConfirm()
                    return true
                }

                // 实体 B 键 / 返回
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK -> {
                    return if (event.repeatCount == 0) viewModel.onBack() else true
                }

                // 实体 X 键 (管理增删分类应用菜单)
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_MENU -> {
                    if (event.repeatCount == 0) {
                        xKeyDownTime = event.eventTime
                    } else if (event.repeatCount > 0 && xKeyDownTime > 0) {
                        if (event.eventTime - xKeyDownTime >= LONG_PRESS_THRESHOLD_MS) {
                            xKeyDownTime = 0L
                            viewModel.openBatchManageDialog()
                            return true
                        }
                    }
                    return true
                }

                // 实体 Y 键 (短按：图标排序模式；长按：应用管理菜单)
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (event.repeatCount == 0) {
                        yKeyDownTime = event.eventTime
                    } else if (event.repeatCount > 0 && yKeyDownTime > 0) {
                        if (event.eventTime - yKeyDownTime >= LONG_PRESS_THRESHOLD_MS) {
                            yKeyDownTime = 0L
                            viewModel.openAppActionDialog()
                            return true
                        }
                    }
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> return true
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_MENU -> {
                    if (xKeyDownTime > 0) {
                        xKeyDownTime = 0L
                        viewModel.openBatchManageDialog()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (yKeyDownTime > 0) {
                        yKeyDownTime = 0L
                        // 短按 Y 键：进入/退出图标排序编辑状态 (iOS 抖动模式)
                        if (viewModel.focusZone.value == FocusZone.APPS) {
                            if (viewModel.isReorderingApps.value) {
                                viewModel.exitReorderMode()
                            } else {
                                viewModel.enterReorderMode()
                            }
                        } else {
                            viewModel.openAppActionDialog()
                        }
                        return true
                    }
                }
            }
        }

        return false
    }
}
