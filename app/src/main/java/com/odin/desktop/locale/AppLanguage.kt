package com.odin.desktop.locale

import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.odin.desktop.R
import java.util.Locale

enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    SYSTEM("", R.string.language_follow_system),
    CHINESE("zh-Hans", R.string.language_chinese),
    ENGLISH("en", R.string.language_english),
    JAPANESE("ja", R.string.language_japanese);

    companion object {
        private val legacyListeners = mutableSetOf<() -> Unit>()

        fun observeLegacyChanges(listener: () -> Unit): AutoCloseable {
            if (Build.VERSION.SDK_INT < 33) legacyListeners += listener
            return AutoCloseable { legacyListeners -= listener }
        }

        fun fromTag(tag: String?): AppLanguage = when {
            tag.isNullOrBlank() -> SYSTEM
            else -> when (Locale.forLanguageTag(tag).language) {
                "zh" -> CHINESE
                "ja" -> JAPANESE
                else -> ENGLISH
            }
        }

        fun current(): AppLanguage = fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())
    }

    fun apply() {
        // Empty locales restore system following; AndroidX persists the override and
        // delegates to Android's per-app language settings on API 33 and later.
        val locales = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() == locales) return
        AppCompatDelegate.setApplicationLocales(locales)
        if (Build.VERSION.SDK_INT < 33) legacyListeners.toList().forEach { it() }
    }
}
