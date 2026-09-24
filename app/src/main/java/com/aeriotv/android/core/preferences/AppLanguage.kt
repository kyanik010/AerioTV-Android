package com.aeriotv.android.core.preferences

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.aeriotv.android.feature.main.AppTab

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

fun AppTab.localizedLabel(language: AppLanguage): String =
    if (language == AppLanguage.ENGLISH) label else when (this) {
        AppTab.LiveTV -> "التلفزيون المباشر"
        AppTab.Favorites -> "المفضلة"
        AppTab.DVR -> "التسجيلات"
        AppTab.OnDemand -> "عند الطلب"
        AppTab.Movies -> "الأفلام"
        AppTab.TVShows -> "المسلسلات"
        AppTab.Audio -> "الصوت"
        AppTab.Settings -> "الإعدادات"
        AppTab.Search -> "البحث"
    }
