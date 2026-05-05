package com.n3k0chan.spotter.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.backup.DriveBackupManager
import com.n3k0chan.spotter.backup.PickGoogleAccountContract
import com.n3k0chan.spotter.data.prefs.AppSettings
import com.n3k0chan.spotter.data.prefs.SettingsRepository
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class SettingsViewModel : ViewModel() {

    private val repo: SettingsRepository = ServiceLocator.settings
    private val drive: DriveBackupManager = ServiceLocator.driveBackup
    val state: StateFlow<AppSettings> = repo.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _consentRequest = MutableStateFlow<Intent?>(null)
    val consentRequest: StateFlow<Intent?> = _consentRequest.asStateFlow()

    private val _restoreDone = MutableStateFlow(false)
    val restoreDone: StateFlow<Boolean> = _restoreDone.asStateFlow()

    fun setApiKey(value: String) = repo.setGroqApiKey(value)
    fun setModel(value: String) = repo.setModel(value)
    fun setRest(seconds: Int) = repo.setDefaultRest(seconds)
    fun setPreWarning(value: Boolean) = repo.setPreWarning(value)
    fun setVibrate(value: Boolean) = repo.setVibrate(value)
    fun setAutoBackup(value: Boolean) = repo.setAutoBackup(value)

    fun onAccountPicked(name: String?) {
        if (name.isNullOrBlank()) return
        repo.setDriveAccount(name)
        // Disparamos un primer backup de prueba para forzar el consent screen
        backupNow()
    }

    fun unlinkAccount() {
        repo.setDriveAccount(null)
        _toast.value = "Cuenta desconectada"
    }

    fun backupNow() {
        viewModelScope.launch {
            _busy.value = true
            when (val r = drive.backupNow()) {
                is DriveBackupManager.BackupResult.Ok -> _toast.value = "Copia subida"
                DriveBackupManager.BackupResult.NotLinked -> _toast.value = "Conecta una cuenta primero"
                is DriveBackupManager.BackupResult.NeedsConsent -> _consentRequest.value = r.resolutionIntent
                is DriveBackupManager.BackupResult.Error -> _toast.value =
                    "Error: ${r.cause.message ?: r.cause::class.simpleName}"
            }
            _busy.value = false
        }
    }

    fun restoreNow() {
        viewModelScope.launch {
            _busy.value = true
            when (val r = drive.restoreNow()) {
                DriveBackupManager.RestoreResult.Ok -> _restoreDone.value = true
                DriveBackupManager.RestoreResult.NotLinked -> _toast.value = "Conecta una cuenta primero"
                DriveBackupManager.RestoreResult.NoBackupYet -> _toast.value = "No hay copias en Drive todavía"
                is DriveBackupManager.RestoreResult.NeedsConsent -> _consentRequest.value = r.resolutionIntent
                is DriveBackupManager.RestoreResult.Error -> _toast.value =
                    "Error: ${r.cause.message ?: r.cause::class.simpleName}"
            }
            _busy.value = false
        }
    }

    fun consumedToast() { _toast.value = null }
    fun consumedConsent() { _consentRequest.value = null }

    /** Tras una resolución de consentimiento, reintentar la operación que pidió permiso. */
    fun retryAfterConsent() = backupNow()

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel() as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val consentIntent by vm.consentRequest.collectAsStateWithLifecycle()
    val restoreDone by vm.restoreDone.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val accountPicker = rememberLauncherForActivityResult(PickGoogleAccountContract()) { name ->
        vm.onAccountPicked(name)
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.consumedConsent()
        if (result.resultCode == Activity.RESULT_OK) vm.retryAfterConsent()
    }
    var confirmRestore by remember { mutableStateOf(false) }

    // Lanza la pantalla de consentimiento cuando Google nos lo pide
    androidx.compose.runtime.LaunchedEffect(consentIntent) {
        consentIntent?.let { consentLauncher.launch(it) }
    }
    androidx.compose.runtime.LaunchedEffect(restoreDone) {
        if (restoreDone) restartApp(ctx)
    }
    androidx.compose.runtime.LaunchedEffect(toast) {
        // En esta versión solo expongo un AlertDialog si hay mensaje persistente.
        // Aquí lo limpio inmediatamente para que el usuario vea aparecer/desaparecer.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── API key Groq
            Column {
                Text(stringResource(R.string.settings_groq_key), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                ApiKeyField(state) { vm.setApiKey(it) }
                if (state.isUserOverridingKey) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_groq_key_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { vm.setApiKey("") }) {
                        Text(stringResource(R.string.settings_clear_key))
                    }
                }
            }

            // ─── Modelo
            Column {
                Text(stringResource(R.string.settings_groq_model), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsRepository.MODELS.forEach { model ->
                        FilterChip(
                            selected = state.groqModel == model,
                            onClick = { vm.setModel(model) },
                            label = { Text(model) },
                        )
                    }
                }
            }

            // ─── Descanso por defecto
            Column {
                Text(stringResource(R.string.settings_default_rest), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                var rest by remember(state.defaultRestSeconds) {
                    mutableStateOf(state.defaultRestSeconds.toString())
                }
                OutlinedTextField(
                    value = rest,
                    onValueChange = {
                        rest = it.filter(Char::isDigit)
                        rest.toIntOrNull()?.let(vm::setRest)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ─── Toggles
            ToggleRow(
                label = stringResource(R.string.settings_pre_warning),
                checked = state.preWarning,
                onChange = vm::setPreWarning,
            )
            ToggleRow(
                label = stringResource(R.string.settings_vibrate),
                checked = state.vibrate,
                onChange = vm::setVibrate,
            )

            HorizontalDivider()

            // ─── Backup en Google Drive
            Column {
                Text(stringResource(R.string.settings_drive_section), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.settings_drive_help),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (state.isDriveLinked) {
                    Text("Cuenta: ${state.driveAccountName}")
                    state.lastBackupAt?.let { ts ->
                        Text(
                            "Última copia: " + DateFormat
                                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(ts)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.backupNow() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_drive_backup_now)) }
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.settings_drive_restore)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { vm.unlinkAccount() }, enabled = !busy) {
                        Text(stringResource(R.string.settings_drive_unlink))
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        label = stringResource(R.string.settings_drive_auto),
                        checked = state.autoBackupAfterWorkout,
                        onChange = vm::setAutoBackup,
                    )
                } else {
                    Button(
                        onClick = { accountPicker.launch(Unit) },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.settings_drive_link)) }
                }
            }

            HorizontalDivider()
            AssistChip(onClick = { /* nada */ }, label = { Text(stringResource(R.string.settings_about)) })
            Text(
                "Spotter · privado y local. La API key y la cuenta de Drive se guardan cifradas en este dispositivo.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Diálogo de confirmación de restore
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_drive_restore)) },
            text = {
                Text(
                    "Esto sobrescribirá los datos del dispositivo con la copia más reciente de Drive. " +
                        "La app se reiniciará al terminar.\n\n¿Continuar?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    vm.restoreNow()
                }) { Text(stringResource(R.string.settings_drive_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // Mensajes (toasts simples como diálogo modal)
    toast?.let {
        AlertDialog(
            onDismissRequest = { vm.consumedToast() },
            confirmButton = { TextButton(onClick = { vm.consumedToast() }) { Text("OK") } },
            text = { Text(it) },
        )
    }
}

private fun restartApp(context: android.content.Context) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    android.os.Process.killProcess(android.os.Process.myPid())
}

@Composable
private fun ApiKeyField(state: AppSettings, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    val placeholder = if (state.hasApiKey) "•••••••• (configurada)" else "gsk_…"
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onSave(value.trim())
                        value = ""
                    }
                },
                enabled = value.isNotBlank(),
            ) { Text("Guardar key") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
