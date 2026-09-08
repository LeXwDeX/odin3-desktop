package com.odin.desktop.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.odin.desktop.R
import com.odin.desktop.ui.components.base.*
import com.odin.desktop.ui.theme.*
import org.json.JSONObject

/** Debug-only component board. It uses production components and never writes launcher settings. */
class UiKitPreviewActivity : ComponentActivity() {
    private val bounds = JSONObject()
    private var disabledClicks = 0
    private fun record() {
        bounds.put("disabled_clicks", disabledClicks)
        bounds.put("locale", resources.configuration.locales.toLanguageTags())
        bounds.put("font_scale", resources.configuration.fontScale)
        filesDir.resolve("ui-kit-bounds.json").writeText(bounds.toString(2))
    }
    private fun measured(name: String) = Modifier.onGloballyPositioned {
        val rect = it.boundsInWindow()
        val measurement = JSONObject().put("x", rect.left).put("y", rect.top)
            .put("width", rect.width).put("height", rect.height)
        if (it[FirstBaseline] != AlignmentLine.Unspecified) measurement.put("first_baseline", rect.top + it[FirstBaseline])
        if (it[LastBaseline] != AlignmentLine.Unspecified) measurement.put("last_baseline", rect.top + it[LastBaseline])
        bounds.put(name, measurement)
        bounds.put("density", resources.displayMetrics.density)
        record()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            OdinDesktopTheme {
                val palette = LocalOdinPalette.current
                var value by remember { mutableStateOf("") }
                OdinSurface(Modifier.fillMaxSize(), SurfaceRole.PANEL) {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OdinSpacing.lg)) {
                        Text("UI Kit · shared components", style = OdinTypography.h1, color = palette.text)
                        OdinEqualHeightRow {
                            OdinTextField(value, { value = it }, getString(R.string.text_new_tab_name),
                                Modifier.weight(1f).fillMaxHeight().then(measured("input")))
                            OdinActionButton(getString(R.string.text_add), {}, Modifier.width(OdinSizes.fieldActionWidth)
                                .fillMaxHeight().then(measured("input_action")))
                        }
                        OdinEqualHeightRow {
                            OdinControl("English", {}, Modifier.weight(1f).fillMaxHeight().then(measured("choice")),
                                selected = true, badge = getString(R.string.text_active))
                            OdinActionButton(getString(R.string.text_set_as_home), {}, Modifier.weight(1f).fillMaxHeight().then(measured("action")))
                            OdinActionButton(getString(R.string.text_delete), { disabledClicks++; record() }, Modifier.weight(1f).fillMaxHeight().then(measured("disabled")),
                                enabled = false, dangerous = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(OdinSpacing.sm)) {
                            OdinBadge(getString(R.string.text_set_as_default), BadgeRole.ACTIVE, measured("default_tag"))
                            OdinBadge(getString(R.string.text_home_tab), BadgeRole.ACTIVE, measured("home_tag"))
                            OdinBadge(getString(R.string.text_game_category), BadgeRole.INFO, measured("category_tag"))
                            OdinBadge("B", BadgeRole.NEUTRAL, measured("key_hint"))
                        }
                        OdinEqualHeightRow {
                            OdinControl(getString(R.string.text_fixed_landscape_default_grip), {},
                                Modifier.weight(1f).fillMaxHeight().then(measured("long_option")), focused = true)
                            OdinControl(getString(R.string.text_move_to_another_tab), {},
                                Modifier.weight(1f).fillMaxHeight().then(measured("two_line")),
                                subtitle = getString(R.string.text_assign_this_icon_to_another_category_tab))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(OdinSpacing.md)) {
                            OdinStatusReadout(getString(R.string.header_battery, "100"), getString(R.string.header_full),
                                Modifier.alignBy(FirstBaseline).then(measured("battery_readout")))
                            OdinStatusReadout(getString(R.string.header_fan, "9999"), "PWM 100%",
                                Modifier.alignBy(FirstBaseline).then(measured("fan_readout")))
                            OdinStatusReadout("12:34", getString(R.string.header_time),
                                Modifier.alignBy(FirstBaseline).then(measured("time_readout")))
                        }
                    }
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
