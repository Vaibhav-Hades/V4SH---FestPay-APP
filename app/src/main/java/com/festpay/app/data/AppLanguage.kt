package com.festpay.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The language the owner picked for V4SH's own screens. Empty means "follow the phone". Stored in V4SH's private settings. */
object AppLanguage {
    private const val PREFS = "v4sh_settings"
    private const val KEY = "app_language"

    data class Choice(val code: String, val label: String)
    val choices = listOf(Choice("", "Phone's language"), Choice("en", "English"), Choice("hi", "हिन्दी"), Choice("te", "తెలుగు"))

    fun chosen(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().takeIf { c -> choices.any { it.code == c } } ?: ""

    fun choose(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, if (choices.any { it.code == code }) code else "").apply()
    }

    /** Wraps a context so Android loads the chosen language's resources. Unchanged when following the phone. */
    fun wrap(base: Context): Context {
        val code = chosen(base)
        if (code.isEmpty()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
