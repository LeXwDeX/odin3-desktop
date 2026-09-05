package com.odin.desktop.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat

/** Application and Service contexts need the same locale as AppCompat activities on API 29–32. */
object AppLanguageContext {
    fun restoreBeforeApplicationCreate(context: Context) {
        if (Build.VERSION.SDK_INT < 33) {
            AppCompatDelegate.setApplicationLocales(LocaleManagerCompat.getApplicationLocales(context))
        }
    }

    fun wrap(base: Context): Context = if (Build.VERSION.SDK_INT >= 33) base else LegacyContext(base)

    private class LegacyContext(base: Context) : ContextWrapper(base) {
        private var cachedConfiguration: Configuration? = null
        private var cachedResources: Resources? = null

        @Synchronized
        override fun getResources(): Resources {
            val original = super.getResources()
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return original
            val configuration = Configuration(original.configuration).apply {
                setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
            }
            // Resource lookups are frequent in the dashboard. Reuse the context until
            // its language or another system configuration value changes.
            if (cachedConfiguration != configuration) {
                cachedResources = baseContext.createConfigurationContext(configuration).resources
                cachedConfiguration = configuration
            }
            return requireNotNull(cachedResources)
        }
    }
}
