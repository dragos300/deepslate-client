package com.deepslate.cobbled.data

import android.content.Context
import com.deepslate.cobbled.core.CobbledJson
import com.deepslate.cobbled.core.LauncherSettings
import com.deepslate.cobbled.core.McAccount

class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAccount(): McAccount? {
        val raw = prefs.getString(KEY_ACCOUNT, null) ?: return null
        return runCatching { CobbledJson.decodeFromString(McAccount.serializer(), raw) }.getOrNull()
    }

    fun saveAccount(account: McAccount?) {
        prefs.edit().apply {
            if (account == null) remove(KEY_ACCOUNT) else putString(
                KEY_ACCOUNT,
                CobbledJson.encodeToString(McAccount.serializer(), account),
            )
            apply()
        }
    }

    fun loadSettings(): LauncherSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return LauncherSettings()
        return runCatching { CobbledJson.decodeFromString(LauncherSettings.serializer(), raw) }
            .getOrDefault(LauncherSettings())
    }

    fun saveSettings(settings: LauncherSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, CobbledJson.encodeToString(LauncherSettings.serializer(), settings))
            .apply()
    }

    companion object {
        private const val PREFS = "cobbled"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_SETTINGS = "settings"
    }
}
