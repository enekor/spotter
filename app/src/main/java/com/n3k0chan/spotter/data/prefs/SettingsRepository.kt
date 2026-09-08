package com.n3k0chan.spotter.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lee y escribe ajustes. Los datos se guardan cifrados con EncryptedSharedPreferences.
 */
class SettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings {
        return AppSettings(
            defaultRestSeconds = prefs.getInt(KEY_REST, 90),
            preWarning = prefs.getBoolean(KEY_PRE_WARNING, true),
            vibrate = prefs.getBoolean(KEY_VIBRATE, true),
            driveAccountName = prefs.getString(KEY_DRIVE_ACCOUNT, null)?.takeIf { it.isNotBlank() },
            autoBackupAfterWorkout = prefs.getBoolean(KEY_AUTO_BACKUP, true),
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 },
            reminderDays = prefs.getStringSet(KEY_REMINDER_DAYS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 18),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0),
            appThemeStyle = com.n3k0chan.spotter.ui.theme.AppThemeStyle.valueOf(
                prefs.getString(KEY_APP_THEME_STYLE, com.n3k0chan.spotter.ui.theme.AppThemeStyle.Modern.name)
                    ?: com.n3k0chan.spotter.ui.theme.AppThemeStyle.Modern.name
            ),
        )
    }

    fun setDefaultRest(seconds: Int) {
        prefs.edit().putInt(KEY_REST, seconds.coerceIn(15, 600)).apply()
        _state.value = load()
    }

    fun setPreWarning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRE_WARNING, enabled).apply()
        _state.value = load()
    }

    fun setVibrate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply()
        _state.value = load()
    }

    fun setDriveAccount(name: String?) {
        prefs.edit().also {
            if (name.isNullOrBlank()) it.remove(KEY_DRIVE_ACCOUNT) else it.putString(KEY_DRIVE_ACCOUNT, name)
        }.apply()
        _state.value = load()
    }

    fun setAutoBackup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        _state.value = load()
    }

    fun setLastBackupAt(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, timestampMillis).apply()
        _state.value = load()
    }

    fun setReminderDays(days: Set<Int>) {
        prefs.edit().putStringSet(KEY_REMINDER_DAYS, days.map { it.toString() }.toSet()).apply()
        _state.value = load()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply()
        _state.value = load()
    }

    fun setAppThemeStyle(style: com.n3k0chan.spotter.ui.theme.AppThemeStyle) {
        prefs.edit().putString(KEY_APP_THEME_STYLE, style.name).apply()
        _state.value = load()
    }

    companion object {
        private const val FILE = "spotter_secure_prefs"
        private const val KEY_REST = "default_rest"
        private const val KEY_PRE_WARNING = "pre_warning"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_DRIVE_ACCOUNT = "drive_account"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_REMINDER_DAYS = "reminder_days"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_APP_THEME_STYLE = "app_theme_style"
    }
}

data class AppSettings(
    val defaultRestSeconds: Int,
    val preWarning: Boolean,
    val vibrate: Boolean,
    val driveAccountName: String? = null,
    val autoBackupAfterWorkout: Boolean = true,
    val lastBackupAt: Long? = null,
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val appThemeStyle: com.n3k0chan.spotter.ui.theme.AppThemeStyle = com.n3k0chan.spotter.ui.theme.AppThemeStyle.Modern,
) {
    val isDriveLinked: Boolean get() = !driveAccountName.isNullOrBlank()
    val hasReminders: Boolean get() = reminderDays.isNotEmpty()
}
