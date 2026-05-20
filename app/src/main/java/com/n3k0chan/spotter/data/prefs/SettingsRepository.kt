package com.n3k0chan.spotter.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.n3k0chan.spotter.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lee y escribe ajustes. La API key se guarda cifrada con EncryptedSharedPreferences.
 * Si el usuario no ha sobrescrito la key, se usa BuildConfig.GROQ_API_KEY (de local.properties).
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
        val storedKey = prefs.getString(KEY_GROQ_API, null).orEmpty()
        return AppSettings(
            groqApiKey = storedKey.ifEmpty { BuildConfig.GROQ_API_KEY },
            isUserOverridingKey = storedKey.isNotEmpty(),
            groqModel = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            defaultRestSeconds = prefs.getInt(KEY_REST, 90),
            preWarning = prefs.getBoolean(KEY_PRE_WARNING, true),
            vibrate = prefs.getBoolean(KEY_VIBRATE, true),
            driveAccountName = prefs.getString(KEY_DRIVE_ACCOUNT, null)?.takeIf { it.isNotBlank() },
            autoBackupAfterWorkout = prefs.getBoolean(KEY_AUTO_BACKUP, true),
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 },
            chatHistoryWindow = ChatHistoryWindow.fromName(
                prefs.getString(KEY_CHAT_HISTORY_WINDOW, ChatHistoryWindow.Month.name),
            ),
            reminderDays = prefs.getStringSet(KEY_REMINDER_DAYS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 18),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0),
        )
    }

    fun setGroqApiKey(value: String) {
        prefs.edit().also {
            if (value.isBlank()) it.remove(KEY_GROQ_API) else it.putString(KEY_GROQ_API, value.trim())
        }.apply()
        _state.value = load()
    }

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _state.value = load()
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

    fun setChatHistoryWindow(window: ChatHistoryWindow) {
        prefs.edit().putString(KEY_CHAT_HISTORY_WINDOW, window.name).apply()
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

    companion object {
        private const val FILE = "spotter_secure_prefs"
        private const val KEY_GROQ_API = "groq_api_key"
        private const val KEY_MODEL = "groq_model"
        private const val KEY_REST = "default_rest"
        private const val KEY_PRE_WARNING = "pre_warning"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_DRIVE_ACCOUNT = "drive_account"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_CHAT_HISTORY_WINDOW = "chat_history_window"
        private const val KEY_REMINDER_DAYS = "reminder_days"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        val MODELS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
        )
    }
}

data class AppSettings(
    val groqApiKey: String,
    val isUserOverridingKey: Boolean,
    val groqModel: String,
    val defaultRestSeconds: Int,
    val preWarning: Boolean,
    val vibrate: Boolean,
    val driveAccountName: String? = null,
    val autoBackupAfterWorkout: Boolean = true,
    val lastBackupAt: Long? = null,
    val chatHistoryWindow: ChatHistoryWindow = ChatHistoryWindow.Month,
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
) {
    val hasApiKey: Boolean get() = groqApiKey.isNotBlank()
    val isDriveLinked: Boolean get() = !driveAccountName.isNullOrBlank()
    val hasReminders: Boolean get() = reminderDays.isNotEmpty()
}

/** Cuánto historial enviar al chat al usar "Compartir historial". */
enum class ChatHistoryWindow(val display: String) {
    Week("1 semana"),
    Month("1 mes"),
    Year("1 año"),
    All("Todo");

    /** Devuelve el timestamp mínimo (ms) para filtrar sesiones. null = sin límite. */
    fun cutoffMillis(now: Long = System.currentTimeMillis()): Long? = when (this) {
        Week -> now - 7L * 24 * 60 * 60 * 1000
        Month -> now - 30L * 24 * 60 * 60 * 1000
        Year -> now - 365L * 24 * 60 * 60 * 1000
        All -> null
    }

    companion object {
        fun fromName(value: String?): ChatHistoryWindow =
            entries.firstOrNull { it.name == value } ?: Month
    }
}
