package com.odin.desktop.locale

import android.app.Application
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.odin.desktop.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = Application::class)
class AppLanguageTest {
    @After
    fun resetLanguage() {
        AppLanguage.SYSTEM.apply()
    }

    @Test
    fun regionalLanguagesResolveToTranslationsAndOtherLanguagesUseEnglish() {
        val context = RuntimeEnvironment.getApplication()
        val cases = mapOf(
            "en-US" to "All apps", "en-GB" to "All apps",
            "zh-CN" to "全部应用", "zh-SG" to "全部应用",
            "zh-TW" to "全部应用", "zh-HK" to "全部应用",
            "ja-JP" to "すべてのアプリ",
            "fr-FR" to "All apps", "de-DE" to "All apps", "ko-KR" to "All apps"
        )
        cases.forEach { (tag, expected) ->
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            assertEquals(tag, expected, context.createConfigurationContext(config).getString(R.string.tab_all_apps))
        }
    }

    @Test
    fun pickerWritesTheAppOverrideAndSystemOptionClearsIt() {
        val controller = Robolectric.buildActivity(LanguageHostActivity::class.java).setup()
        try {
            listOf(AppLanguage.JAPANESE, AppLanguage.CHINESE, AppLanguage.ENGLISH, AppLanguage.SYSTEM).forEach { language ->
                language.apply()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(language, AppLanguage.current())
                if (Build.VERSION.SDK_INT >= 33) {
                    val manager = controller.get().getSystemService(LocaleManager::class.java)
                    assertEquals(language.tag, manager.applicationLocales.toLanguageTags())
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [32])
    fun legacyApplicationContextTracksChangesWithoutLosingOtherConfiguration() {
        val application = RuntimeEnvironment.getApplication()
        val wrapped = AppLanguageContext.wrap(application)
        val initial = Configuration(application.resources.configuration)
        AppLanguage.JAPANESE.apply()
        assertEquals("すべてのアプリ", wrapped.getString(R.string.tab_all_apps))
        assertEquals(initial.densityDpi, wrapped.resources.configuration.densityDpi)
        assertEquals(initial.orientation, wrapped.resources.configuration.orientation)
        AppLanguage.CHINESE.apply()
        assertEquals("全部应用", wrapped.getString(R.string.tab_all_apps))
        AppLanguage.SYSTEM.apply()
        assertEquals(application.resources.configuration.locales, wrapped.resources.configuration.locales)
    }

    @Test
    @Config(sdk = [32])
    fun recreatedLegacyActivityUsesTheSavedChoice() {
        val first = Robolectric.buildActivity(LanguageHostActivity::class.java).setup()
        AppLanguage.JAPANESE.apply()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("すべてのアプリ", first.get().getString(R.string.tab_all_apps))
        first.pause().stop().destroy()
        val next = Robolectric.buildActivity(LanguageHostActivity::class.java).setup()
        try {
            assertEquals("すべてのアプリ", next.get().getString(R.string.tab_all_apps))
        } finally {
            next.pause().stop().destroy()
        }
    }
}

class LanguageHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_OdinDesktop)
        super.onCreate(savedInstanceState)
    }
}
