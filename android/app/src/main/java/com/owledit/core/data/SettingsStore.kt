package com.owledit.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.owledit.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.owlDataStore: DataStore<Preferences> by preferencesDataStore(name = "owledit_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val AUTO_FORCE_STOP = booleanPreferencesKey("auto_force_stop")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_before_save")
        val SHOW_DIFF = booleanPreferencesKey("show_diff_before_save")
        val SELECTED_PKG = stringPreferencesKey("selected_package")
        val LANGUAGE = stringPreferencesKey("app_language")
    }

    val settingsFlow: Flow<AppSettings> = context.owlDataStore.data.map { prefs ->
        AppSettings(
            autoForceStop = prefs[Keys.AUTO_FORCE_STOP] ?: true,
            autoBackupBeforeSave = prefs[Keys.AUTO_BACKUP] ?: true,
            showDiffBeforeSave = prefs[Keys.SHOW_DIFF] ?: true,
            selectedPackage = prefs[Keys.SELECTED_PKG] ?: "com.chillyroom.zhmr.gp",
            language = prefs[Keys.LANGUAGE] ?: "vi",
        )
    }

    suspend fun setAutoForceStop(enabled: Boolean) {
        context.owlDataStore.edit { it[Keys.AUTO_FORCE_STOP] = enabled }
    }

    suspend fun setAutoBackupBeforeSave(enabled: Boolean) {
        context.owlDataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setShowDiffBeforeSave(enabled: Boolean) {
        context.owlDataStore.edit { it[Keys.SHOW_DIFF] = enabled }
    }

    suspend fun setSelectedPackage(pkg: String) {
        context.owlDataStore.edit { it[Keys.SELECTED_PKG] = pkg }
    }

    suspend fun setLanguage(lang: String) {
        val norm = if (lang.equals("en", ignoreCase = true)) "en" else "vi"
        context.owlDataStore.edit { it[Keys.LANGUAGE] = norm }
    }
}
