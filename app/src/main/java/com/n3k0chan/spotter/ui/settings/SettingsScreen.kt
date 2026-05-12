package com.n3k0chan.spotter.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.backup.DriveBackupManager
import com.n3k0chan.spotter.backup.PickGoogleAccountContract
import com.n3k0chan.spotter.data.prefs.AppSettings
import com.n3k0chan.spotter.data.prefs.ChatHistoryWindow
import com.n3k0chan.spotter.data.prefs.SettingsRepository
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterButtonVariant
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterChip
import com.n3k0chan.spotter.ui.components.SpotterChipTone
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
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
    fun setChatHistoryWindow(value: ChatHistoryWindow) = repo.setChatHistoryWindow(value)

    fun onAccountPicked(name: String?) {
        if (name.isNullOrBlank()) return
        repo.setDriveAccount(name)
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val consentIntent by vm.consentRequest.collectAsStateWithLifecycle()
    val restoreDone by vm.restoreDone.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = SpotterTheme.colors

    val accountPicker = rememberLauncherForActivityResult(PickGoogleAccountContract()) { name ->
        vm.onAccountPicked(name)
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.consumedConsent()
        if (result.resultCode == Activity.RESULT_OK) vm.retryAfterConsent()
    }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(consentIntent) {
        consentIntent?.let { consentLauncher.launch(it) }
    }
    LaunchedEffect(restoreDone) {
        if (restoreDone) restartApp(ctx)
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Ajustes",
                leading = { SpotterIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── ASISTENTE
            item { SectionHeader("ASISTENTE") }
            item {
                SpotterCard(padding = 0.dp) {
                    Column {
                        ApiKeyRow(state = state, onSave = vm::setApiKey, onClear = { vm.setApiKey("") })
                        HorizontalDivider(color = c.border, thickness = 1.dp)
                        ModelRow(selected = state.groqModel, onSelect = vm::setModel)
                        HorizontalDivider(color = c.border, thickness = 1.dp)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text("Historial al chat", style = SpotterText.bodyMd, color = c.text)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Cuánto historial se envía al activar el toggle",
                                style = SpotterText.small,
                                color = c.textMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            ChatHistoryWindowSelector(
                                selected = state.chatHistoryWindow,
                                onSelect = vm::setChatHistoryWindow,
                            )
                        }
                    }
                }
            }
            item {
                SpotterCard {
                    Column {
                        Text("Historial al compartir con el chat", style = SpotterText.bodyMd, color = c.text)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Cuánto historial se envía cuando activas \"Compartir historial\" en el chat.",
                            style = SpotterText.small,
                            color = c.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        ChatHistoryWindowSelector(
                            selected = state.chatHistoryWindow,
                            onSelect = vm::setChatHistoryWindow,
                        )
                    }
                }
            }

            // ── ENTRENO
            item { SectionHeader("ENTRENO") }
            item {
                SpotterCard(padding = 0.dp) {
                    Column {
                        IntFieldRow(
                            label = "Descanso por defecto",
                            value = state.defaultRestSeconds,
                            unit = "s",
                            onChange = vm::setRest,
                        )
                        HorizontalDivider(color = c.border, thickness = 1.dp)
                        ToggleSettingRow(
                            label = "Aviso a 10s del final",
                            checked = state.preWarning,
                            onChange = vm::setPreWarning,
                        )
                        HorizontalDivider(color = c.border, thickness = 1.dp)
                        ToggleSettingRow(
                            label = "Vibrar al terminar",
                            checked = state.vibrate,
                            onChange = vm::setVibrate,
                        )
                    }
                }
            }

            // ── DRIVE
            item { SectionHeader("COPIA EN GOOGLE DRIVE") }
            if (state.isDriveLinked) {
                item {
                    SpotterCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(c.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Cloud,
                                        contentDescription = null,
                                        tint = c.success,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(state.driveAccountName ?: "—", style = SpotterText.bodyMd, color = c.text)
                                    Spacer(Modifier.height(2.dp))
                                    val subtitle = state.lastBackupAt?.let {
                                        "Última copia · " + DateFormat
                                            .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(it))
                                    } ?: "Sin copias todavía"
                                    Text(subtitle, style = SpotterText.small, color = c.textMuted)
                                }
                                SpotterChip(text = "OK", tone = SpotterChipTone.Success)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpotterButton(
                                    text = "Subir ahora",
                                    leading = Icons.Filled.CloudUpload,
                                    variant = SpotterButtonVariant.Tonal,
                                    height = 44.dp,
                                    onClick = vm::backupNow,
                                    modifier = Modifier.weight(1f),
                                )
                                SpotterButton(
                                    text = "Restaurar",
                                    leading = Icons.Filled.CloudDownload,
                                    variant = SpotterButtonVariant.Outlined,
                                    height = 44.dp,
                                    onClick = { confirmRestore = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            SpotterButton(
                                text = "Desconectar cuenta",
                                variant = SpotterButtonVariant.Text,
                                full = true,
                                height = 44.dp,
                                onClick = vm::unlinkAccount,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(2.dp)) }
                item {
                    SpotterCard(padding = 0.dp) {
                        ToggleSettingRow(
                            label = "Subir tras cada entreno",
                            checked = state.autoBackupAfterWorkout,
                            onChange = vm::setAutoBackup,
                        )
                    }
                }
            } else {
                item {
                    SpotterCard {
                        Column {
                            Text(
                                "Conecta una cuenta de Google para guardar la base de datos en una carpeta privada de tu Drive.",
                                style = SpotterText.body,
                                color = c.textMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            SpotterButton(
                                text = "Conectar cuenta de Google",
                                full = true,
                                onClick = { accountPicker.launch(Unit) },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Spotter · privado y local. La API key y la cuenta de Drive se guardan cifradas en este dispositivo.",
                    style = SpotterText.small,
                    color = c.textFaint,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurar de Drive", style = SpotterText.title2) },
            text = {
                Text(
                    "Esto sobrescribirá los datos del dispositivo con la copia más reciente de Drive. " +
                        "La app se reiniciará al terminar.",
                    style = SpotterText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    vm.restoreNow()
                }) { Text("Restaurar", color = c.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancelar", color = c.textMuted) }
            },
        )
    }

    toast?.let {
        AlertDialog(
            onDismissRequest = { vm.consumedToast() },
            confirmButton = { TextButton(onClick = { vm.consumedToast() }) { Text("OK", color = c.primary) } },
            text = { Text(it, style = SpotterText.body) },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    val c = SpotterTheme.colors
    Text(
        text,
        style = SpotterText.caps,
        color = c.textMuted,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ApiKeyRow(state: AppSettings, onSave: (String) -> Unit, onClear: () -> Unit) {
    val c = SpotterTheme.colors
    var showField by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("API key de Groq", style = SpotterText.bodyMd, color = c.text)
                if (state.hasApiKey) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (state.isUserOverridingKey) "Configurada (override sobre BuildConfig)"
                        else "Cargada desde BuildConfig",
                        style = SpotterText.small, color = c.textMuted,
                    )
                }
            }
            if (state.hasApiKey) {
                Text(
                    "•••••• ${state.groqApiKey.takeLast(4)}",
                    style = SpotterText.numS,
                    color = c.textMuted,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Filled.Visibility,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(16.dp).clickable { showField = !showField },
            )
        }
        if (showField) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("gsk_…", color = c.textFaint) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surfaceMuted,
                    unfocusedContainerColor = c.surfaceMuted,
                    focusedBorderColor = c.borderStrong,
                    unfocusedBorderColor = c.border,
                    cursorColor = c.primary,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = SpotterText.body,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotterButton(
                    text = "Guardar",
                    height = 40.dp,
                    onClick = {
                        if (value.isNotBlank()) {
                            onSave(value.trim())
                            value = ""
                            showField = false
                        }
                    },
                )
                if (state.isUserOverridingKey) {
                    SpotterButton(
                        text = "Borrar",
                        variant = SpotterButtonVariant.Outlined,
                        height = 40.dp,
                        onClick = onClear,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(selected: String, onSelect: (String) -> Unit) {
    val c = SpotterTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Modelo", style = SpotterText.bodyMd, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(selected, style = SpotterText.small, color = c.textMuted)
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SettingsRepository.MODELS.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun IntFieldRow(label: String, value: Int, unit: String, onChange: (Int) -> Unit) {
    val c = SpotterTheme.colors
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { text = value.toString() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SpotterText.bodyMd, color = c.text, modifier = Modifier.weight(1f))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.filter(Char::isDigit)
                text.toIntOrNull()?.let(onChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = SpotterText.numS.copy(color = c.text, textAlign = androidx.compose.ui.text.style.TextAlign.End),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
            modifier = Modifier.width(60.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(unit, style = SpotterText.small, color = c.textMuted)
    }
}

@Composable
private fun ChatHistoryWindowSelector(
    selected: ChatHistoryWindow,
    onSelect: (ChatHistoryWindow) -> Unit,
) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatHistoryWindow.entries.forEach { w ->
            val isSelected = selected == w
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) c.primary else Color.Transparent)
                    .clickable { onSelect(w) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    w.display,
                    style = SpotterText.smallMd,
                    color = if (isSelected) c.onPrimary else c.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SpotterText.bodyMd, color = c.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = c.borderStrong,
                uncheckedBorderColor = Color.Transparent,
            ),
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
