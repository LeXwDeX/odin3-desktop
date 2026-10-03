package com.odin.desktop.locale

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import com.odin.desktop.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = Application::class)
class PluralResourcesTest {
    @Test
    fun appLibraryCountUsesEnglishSingularAndLocalizedOtherForms() {
        val context = RuntimeEnvironment.getApplication()
        fun localized(tag: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
        )

        val english = localized("en")
        assertEquals("Games · 0 apps", english.resources.getQuantityString(
            R.plurals.app_library_count, 0, "Games", 0
        ))
        assertEquals("Games · 1 app", english.resources.getQuantityString(
            R.plurals.app_library_count, 1, "Games", 1
        ))
        assertEquals("Games · 2 apps", english.resources.getQuantityString(
            R.plurals.app_library_count, 2, "Games", 2
        ))

        listOf("zh-Hans", "zh-Hant", "ja").forEach { tag ->
            val resources = localized(tag).resources
            listOf(0, 1, 2).forEach { count ->
                val group = when (tag) {
                    "zh-Hans", "zh-Hant" -> "游戏"
                    else -> "ゲーム"
                }
                val result = resources.getQuantityString(R.plurals.app_library_count, count, group, count)
                val expected = if (tag == "ja") "$group · $count 個" else "$group · $count 个应用"
                assertEquals(tag, expected, result)
            }
        }
    }

    @Test
    fun countdownUsesOneAndOtherEnglishFormsAndLocalizedTranslations() {
        val context = RuntimeEnvironment.getApplication()
        fun countdown(tag: String, seconds: Int): String {
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(tag))
                }
            )
            return localized.resources.getQuantityString(
                R.plurals.afk_countdown_hint, seconds, seconds
            )
        }

        assertEquals("Entering idle screen in 1 second\nDouble-tap to cancel or exit", countdown("en", 1))
        assertEquals("Entering idle screen in 0 seconds\nDouble-tap to cancel or exit", countdown("en", 0))
        assertEquals("Entering idle screen in 2 seconds\nDouble-tap to cancel or exit", countdown("en", 2))
        assertEquals("1 秒后进入息屏挂机\n双击屏幕可取消或退出", countdown("zh-Hans", 1))
        assertEquals("2 秒後に画面保護を開始\nダブルタップでキャンセル・終了", countdown("ja", 2))
    }
}
