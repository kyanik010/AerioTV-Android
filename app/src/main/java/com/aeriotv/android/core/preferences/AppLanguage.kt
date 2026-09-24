package com.aeriotv.android.core.preferences

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage {
    ENGLISH,
    ARABIC,
}

object LanguageManager {
    private const val PREFS = "aeriotv_language"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage =
        when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) {
            AppLanguage.ARABIC.name -> AppLanguage.ARABIC
            else -> AppLanguage.ENGLISH
        }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.name)
            .apply()
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

