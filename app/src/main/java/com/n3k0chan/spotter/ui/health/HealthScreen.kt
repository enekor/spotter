package com.n3k0chan.spotter.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.health.DaySummary
import com.n3k0chan.spotter.data.health.ExerciseSession
import com.n3k0chan.spotter.data.health.SleepSession
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HealthScreen(
    onBack: () -> Unit,
    vm: HealthViewModel = viewModel(factory = HealthViewModel.Factory),
) {
    android.util.Log.d("HealthScreen", "HealthScreen composable entered, available=${vm.state.value.available}")
    val state by vm.state.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors
    var showImportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        vm.onPermissionsResult(granted)
    }

    LaunchedEffect(state.importResult) {
        state.importResult?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeImportResult()
        }
    }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SpotterTopBar(
                title = "Salud",
                leading = {
                    SpotterIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                    )
                },
                trailing = {
                    if (state.hasPermissions && state.available) {
                        SpotterIconButton(
                            icon = Icons.Filled.Download,
                            onClick = { showImportDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.available -> UnavailableState(Modifier.padding(padding))
            !state.hasPermissions -> PermissionsState(
                modifier = Modifier.padding(padding),
                onRequest = {
                    permissionLauncher.launch(ServiceLocator.healthConnect.permissions)
                },
            )
            state.loading || state.importing || state.scanningImport ->
                LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                modifier = Modifier.padding(padding),
                message = state.error!!,
                onRetry = { vm.load() },
            )
            state.summary != null -> DaySummaryContent(
                modifier = Modifier.padding(padding),
                summary = state.summary!!,
                date = state.date,
                onPreviousDay = { vm.previousDay() },
                onNextDay = { vm.nextDay() },
            )
        }
    }

    if (showImportDialog) {
        ImportSessionsDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { startDate, endDate ->
                showImportDialog = false
                vm.scanForImport(startDate, endDate)
            },
        )
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onToggleSource = { vm.toggleImportSource(it) },
            onConfirm = { vm.confirmImport() },
            onDismiss = { vm.dismissImportPreview() },
        )
    }
}

@Composable
private fun UnavailableState(modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SyncProblem,
            contentDescription = null,
            tint = c.textFaint,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Health Connect no disponible", style = SpotterText.title3, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tu dispositivo no tiene Health Connect. En Android 14+ viene integrado. En versiones anteriores, instálalo desde Play Store.",
            style = SpotterText.body,
            color = c.textMuted,
        )
    }
}

@Composable
private fun PermissionsState(modifier: Modifier = Modifier, onRequest: () -> Unit) {
    val c = SpotterTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = c.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Conectar con Health Connect", style = SpotterText.title3, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Permite a Spotter leer datos de salud de tus dispositivos: pasos, calorías, frecuencia cardíaca, ejercicio y sueño.",
            style = SpotterText.body,
            color = c.textMuted,
        )
        Spacer(Modifier.height(24.dp))
        SpotterButton(
            text = "Conectar",
            leading = Icons.Filled.Favorite,
            onClick = onRequest,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = c.primary)
    }
}

@Composable
private fun ErrorState(modifier: Modifier = Modifier, message: String, onRetry: () -> Unit) {
    val c = SpotterTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Error al leer datos", style = SpotterText.title3, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(message, style = SpotterText.body, color = c.textMuted)
        Spacer(Modifier.height(24.dp))
        SpotterButton(text = "Reintentar", onClick = onRetry)
    }
}

@Composable
private fun DaySummaryContent(
    modifier: Modifier = Modifier,
    summary: DaySummary,
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    val c = SpotterTheme.colors
    val isToday = date == LocalDate.now()
    val dateLabel = if (isToday) "Hoy" else date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("es"))
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpotterIconButton(icon = Icons.Filled.ChevronLeft, onClick = onPreviousDay)
                Text(dateLabel, style = SpotterText.title3, color = c.text)
                if (isToday) {
                    Box(Modifier.size(40.dp))
                } else {
                    SpotterIconButton(icon = Icons.Filled.ChevronRight, onClick = onNextDay)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    icon = Icons.Filled.DirectionsWalk,
                    iconTint = c.primary,
                    label = "Pasos",
                    value = summary.steps?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    iconTint = Color(0xFFEF4444),
                    label = "Calorías",
                    value = summary.totalCalories?.let { "%.0f".format(it) } ?: "—",
                    unit = "kcal",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    icon = Icons.Filled.Favorite,
                    iconTint = Color(0xFFEC4899),
                    label = "Frecuencia",
                    value = summary.heartRate?.avg?.toString() ?: "—",
                    unit = "bpm",
                    detail = summary.heartRate?.let { "${it.min}–${it.max} bpm" },
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = Icons.Filled.Route,
                    iconTint = Color(0xFF3B82F6),
                    label = "Distancia",
                    value = summary.distanceMeters?.let {
                        if (it >= 1000) "%.1f".format(it / 1000) else "%.0f".format(it)
                    } ?: "—",
                    unit = if ((summary.distanceMeters ?: 0.0) >= 1000) "km" else "m",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (summary.exerciseSessions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Ejercicio", style = SpotterText.title3, color = c.text)
            }
            items(summary.exerciseSessions) { session ->
                ExerciseSessionCard(session)
            }
        }

        if (summary.sleepSessions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Sueño", style = SpotterText.title3, color = c.text)
            }
            items(summary.sleepSessions) { session ->
                SleepSessionCard(session)
            }
        }

        if (summary.exerciseSessions.isEmpty() && summary.sleepSessions.isEmpty()
            && summary.steps == null && summary.totalCalories == null
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Sin datos para este día.",
                    style = SpotterText.body,
                    color = c.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String? = null,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    SpotterCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(label, style = SpotterText.smallMd, color = c.textMuted)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = SpotterText.numM, color = c.text)
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, style = SpotterText.small, color = c.textMuted, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = SpotterText.small, color = c.textFaint)
            }
        }
    }
}

@Composable
private fun ExerciseSessionCard(session: ExerciseSession) {
    val c = SpotterTheme.colors
    val duration = Duration.between(session.startTime, session.endTime)
    val minutes = duration.toMinutes()
    val timeStr = session.startTime.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    SpotterCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = c.primarySoftText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title ?: exerciseTypeName(session.type),
                    style = SpotterText.bodyMd,
                    color = c.text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$timeStr · ${minutes}min", style = SpotterText.small, color = c.textMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                session.calories?.let {
                    Text("%.0f kcal".format(it), style = SpotterText.numS, color = c.text)
                }
                session.heartRateAvg?.let {
                    Text("$it bpm", style = SpotterText.small, color = c.textFaint)
                }
            }
        }
    }
}

@Composable
private fun SleepSessionCard(session: SleepSession) {
    val c = SpotterTheme.colors
    val hours = session.durationMinutes / 60
    val mins = session.durationMinutes % 60
    val durationStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    val bedtime = session.startTime.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val wakeup = session.endTime.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    SpotterCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(durationStr, style = SpotterText.numM, color = c.text)
                Text("$bedtime → $wakeup", style = SpotterText.small, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    onToggleSource: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpotterTheme.colors
    val selectedCount = preview.sessions.count {
        (it.sourcePackage ?: "desconocido") in preview.selectedSources
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar sesiones", style = SpotterText.title2) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedCount > 0,
            ) { Text("Importar $selectedCount", color = if (selectedCount > 0) c.primary else c.textFaint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = c.textMuted) }
        },
        text = {
            Column {
                Text(
                    "${preview.sessions.size} sesiones encontradas",
                    style = SpotterText.body,
                    color = c.textMuted,
                )
                if (preview.sources.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text("Fuentes", style = SpotterText.bodyMd, color = c.text)
                    Spacer(Modifier.height(8.dp))
                }
                preview.sources.forEach { source ->
                    val checked = source.packageName in preview.selectedSources
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleSource(source.packageName) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (checked) Icons.Filled.CheckBox
                            else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (checked) c.primary else c.textFaint,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                appLabel(source.packageName),
                                style = SpotterText.bodyMd,
                                color = c.text,
                            )
                            Text(
                                "${source.sessionCount} sesiones",
                                style = SpotterText.small,
                                color = c.textMuted,
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSessionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val c = SpotterTheme.colors
    val datePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMs = datePickerState.selectedStartDateMillis
                    val endMs = datePickerState.selectedEndDateMillis
                    if (startMs != null && endMs != null) {
                        val zone = ZoneId.systemDefault()
                        val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
                        val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
                        onConfirm(start, end)
                    }
                },
                enabled = datePickerState.selectedStartDateMillis != null
                        && datePickerState.selectedEndDateMillis != null,
            ) {
                Text("Importar", color = c.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = c.textMuted)
            }
        },
    ) {
        DateRangePicker(
            state = datePickerState,
            title = {
                Text(
                    "Importar sesiones de Health Connect",
                    style = SpotterText.title3,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            headline = {
                Text(
                    "Selecciona un rango de fechas",
                    style = SpotterText.body,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun exerciseTypeName(type: Int): String = when (type) {
    2 -> "Badminton"
    4 -> "Béisbol"
    5 -> "Baloncesto"
    8 -> "Ciclismo"
    10 -> "Escalada"
    11 -> "Cricket"
    14 -> "Baile"
    16 -> "Esgrima"
    17 -> "Fútbol americano"
    20 -> "Golf"
    22 -> "Gimnasia"
    24 -> "Balonmano"
    26 -> "HIIT"
    27 -> "Senderismo"
    29 -> "Hockey"
    31 -> "Artes marciales"
    35 -> "Remo"
    37 -> "Correr"
    39 -> "Rugby"
    44 -> "Fútbol"
    46 -> "Softball"
    48 -> "Squash"
    50 -> "Entrenamiento de fuerza"
    52 -> "Natación (piscina)"
    53 -> "Natación (abierta)"
    54 -> "Tenis de mesa"
    55 -> "Tenis"
    58 -> "Voleibol"
    62 -> "Caminar"
    64 -> "Waterpolo"
    66 -> "Levantamiento de pesas"
    68 -> "Yoga"
    0 -> "Ejercicio"
    else -> "Ejercicio"
}
