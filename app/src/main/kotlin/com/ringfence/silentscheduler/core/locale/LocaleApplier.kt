package com.ringfence.silentscheduler.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Switches the app's actual display language. Backed by AppCompatDelegate's per-app
 * language API, which uses the framework LocaleManager on API 33+ and its own
 * compat storage (see the AppLocalesMetadataHolderService entry in the manifest)
 * below that — either way it persists across process restarts on its own, so this
 * only needs to be called when the user picks a language, never on every launch.
 */
fun applyAppLocale(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.localeTag))
}
