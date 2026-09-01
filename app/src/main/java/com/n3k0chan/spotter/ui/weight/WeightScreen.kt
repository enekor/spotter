package com.n3k0chan.spotter.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.data.db.entities.WeightLog
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.components.SpotterTopBar
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    onOpenSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    vm: WeightViewModel = viewModel(factory = WeightViewModel.Factory),
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val logsAsc by vm.logsAsc.collectAsStateWithLifecycle()
    val aiSummary by vm.aiSummary.collectAsStateWithLifecycle()
    val isLoadingAi by vm.isLoadingAi.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    var showAddSheet by remember { mutableStateOf(false) }
    var logToEdit by remember { mutableStateOf<WeightLog?>(null) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            SpotterTopBar(
                title = "Peso",
                trailing = {
                    Row {
                        SpotterIconButton(Icons.AutoMirrored.Filled.Chat, onClick = onOpenChat)
                        SpotterIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    logToEdit = null
                    showAddSheet = true 
                },
                containerColor = c.primary,
                contentColor = c.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir peso")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (logsAsc.size >= 2) {
                item {
                    SpotterCard(padding = 16.dp) {
                        Column {
                            Text("PROGRESO", style = SpotterText.caps, color = c.textMuted)
                            Spacer(Modifier.height(12.dp))
                            InteractiveSparkline(
                                logs = logsAsc,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }
            }

            item {
                SpotterCard(padding = 16.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Análisis de IA", style = SpotterText.title3, color = c.text)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (isLoadingAi) {
                            CircularProgressIndicator(color = c.primary, modifier = Modifier.size(24.dp))
                        } else if (aiSummary != null) {
                            Text(aiSummary!!, style = SpotterText.bodyMd, color = c.text)
                        } else {
                            Text("Pide a la IA que analice tu progreso de peso.", style = SpotterText.bodyMd, color = c.textMuted)
                        }
                        Spacer(Modifier.height(12.dp))
                        SpotterButton(
                            text = "Analizar",
                            onClick = { vm.analyzeProgress() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingAi && logs.isNotEmpty()
                        )
                    }
                }
            }

            item {
                Text("HISTORIAL", style = SpotterText.caps, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        "Aún no has registrado tu peso.",
                        style = SpotterText.bodyMd,
                        color = c.textMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                items(logs.size) { index ->
                    val log = logs[index]
                    val prevLog = logs.getOrNull(index + 1)
                    val diff = if (prevLog != null) log.weightKg - prevLog.weightKg else null

                    WeightLogItem(
                        log = log,
                        diff = diff,
                        onClick = {
                            logToEdit = log
                            showAddSheet = true
                        },
                        onDelete = { vm.deleteLog(log) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddWeightSheet(
            initialLog = logToEdit,
            onDismiss = { showAddSheet = false },
            onSave = { weight, dateMs, notes ->
                if (logToEdit != null) {
                    vm.updateLog(logToEdit!!, weight, dateMs, notes)
                } else {
                    vm.addLog(weight, dateMs, notes)
                }
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun WeightLogItem(log: WeightLog, diff: Float?, onClick: () -> Unit, onDelete: () -> Unit) {
    val c = SpotterTheme.colors
    val date = Instant.ofEpochMilli(log.dateMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateStr = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

    SpotterCard(padding = 16.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${log.weightKg} kg", style = SpotterText.title2, color = c.text)
                    if (diff != null && diff != 0f) {
                        Spacer(Modifier.width(8.dp))
                        val isGain = diff > 0
                        val color = if (isGain) c.danger else c.success
                        val sign = if (isGain) "+" else ""
                        Text(
                            "$sign${"%.1f".format(diff)} kg",
                            style = SpotterText.bodyMd,
                            color = color,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(dateStr, style = SpotterText.small, color = c.textMuted)
                if (!log.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(log.notes, style = SpotterText.bodyMd, color = c.textFaint)
                }
            }
            SpotterIconButton(
                icon = Icons.Filled.Delete,
                onClick = onDelete,
                tone = com.n3k0chan.spotter.ui.components.IconButtonTone.Muted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWeightSheet(
    initialLog: WeightLog?,
    onDismiss: () -> Unit,
    onSave: (Float, Long, String?) -> Unit
) {
    val c = SpotterTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightStr by remember { mutableStateOf(initialLog?.weightKg?.toString() ?: "") }
    var notes by remember { mutableStateOf(initialLog?.notes ?: "") }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { 
        mutableStateOf(
            initialLog?.dateMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
        ) 
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Registrar peso", style = SpotterText.title2, color = c.text)

            OutlinedTextField(
                value = weightStr,
                onValueChange = { weightStr = it },
                label = { Text("Peso (kg)", color = c.textMuted) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                    focusedBorderColor = c.primary,
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                )
            )

            SpotterCard(onClick = { showDatePicker = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fecha", style = SpotterText.bodyMd, color = c.text)
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), style = SpotterText.bodyMd, color = c.primary)
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas (opcional)", color = c.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                    focusedBorderColor = c.primary,
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                )
            )

            SpotterButton(
                text = "Guardar",
                onClick = {
                    val w = weightStr.replace(",", ".").toFloatOrNull()
                    if (w != null && w > 0) {
                        val ms = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onSave(w, ms, notes.takeIf { it.isNotBlank() })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = weightStr.isNotBlank()
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("Aceptar", color = c.primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar", color = c.text)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = c.surface,
            )
        ) {
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = c.surface,
                    titleContentColor = c.text,
                    headlineContentColor = c.text,
                    weekdayContentColor = c.textMuted,
                    subheadContentColor = c.text,
                    yearContentColor = c.text,
                    currentYearContentColor = c.text,
                    selectedYearContentColor = c.onPrimary,
                    selectedYearContainerColor = c.primary,
                    dayContentColor = c.text,
                    disabledDayContentColor = c.textFaint,
                    selectedDayContentColor = c.onPrimary,
                    disabledSelectedDayContentColor = c.textFaint,
                    selectedDayContainerColor = c.primary,
                    disabledSelectedDayContainerColor = c.surface,
                    todayContentColor = c.primary,
                    todayDateBorderColor = c.primary,
                )
            )
        }
    }
}

@Composable
fun InteractiveSparkline(logs: List<WeightLog>, modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    if (logs.size < 2) return

    val points = logs.map { it.weightKg }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val pad = 4.dp.toPx()
                        val w = size.width
                        val step = (w - pad * 2) / (points.size - 1)
                        val idx = ((offset.x - pad) / step).toInt().coerceIn(0, points.size - 1)
                        selectedIndex = idx
                    },
                    onDrag = { change, _ ->
                        val pad = 4.dp.toPx()
                        val w = size.width
                        val step = (w - pad * 2) / (points.size - 1)
                        val idx = ((change.position.x - pad) / step).toInt().coerceIn(0, points.size - 1)
                        selectedIndex = idx
                    },
                    onDragEnd = { selectedIndex = null },
                    onDragCancel = { selectedIndex = null }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val pad = 4.dp.toPx()
                        val w = size.width
                        val step = (w - pad * 2) / (points.size - 1)
                        val idx = ((offset.x - pad) / step).toInt().coerceIn(0, points.size - 1)
                        selectedIndex = idx
                        tryAwaitRelease()
                        selectedIndex = null
                    }
                )
            }
    ) {
        val pad = 4.dp.toPx()
        val topPad = 24.dp.toPx() // Extra padding at top for text
        val w = size.width
        val h = size.height
        val min = points.min()
        val max = points.max()
        val span = (max - min).coerceAtLeast(0.001f)
        
        val xs = points.indices.map { i -> pad + i.toFloat() / (points.size - 1) * (w - pad * 2) }
        val ys = points.map { p -> topPad + (1f - (p - min) / span) * (h - pad - topPad) }

        val fill = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until points.size) lineTo(xs[i], ys[i])
            lineTo(xs.last(), h)
            lineTo(xs.first(), h)
            close()
        }
        drawPath(path = fill, color = c.chartFill)

        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until points.size) lineTo(xs[i], ys[i])
        }
        drawPath(
            path = line,
            color = c.primary,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Draw normal points
        for (i in points.indices) {
            drawCircle(color = c.primary, radius = 2.dp.toPx(), center = Offset(xs[i], ys[i]))
        }

        // Draw selected point and tooltip
        selectedIndex?.let { idx ->
            val x = xs[idx]
            val y = ys[idx]
            val log = logs[idx]
            val date = Instant.ofEpochMilli(log.dateMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val dateStr = date.format(DateTimeFormatter.ofPattern("dd MMM"))
            
            // Vertical line
            drawLine(
                color = c.textMuted.copy(alpha = 0.5f),
                start = Offset(x, topPad),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx()
            )
            
            // Highlighted circle
            drawCircle(color = c.bg, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = c.primary, radius = 4.dp.toPx(), center = Offset(x, y))
            
            // Text tooltip
            val text = "${log.weightKg}kg\n$dateStr"
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    color = c.text,
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
            
            val textWidth = textLayoutResult.size.width
            val textHeight = textLayoutResult.size.height
            
            // Adjust text position so it doesn't go off-screen
            var textX = x - textWidth / 2
            if (textX < 0) textX = 0f
            if (textX + textWidth > w) textX = w - textWidth
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(textX, 0f)
            )
        }
    }
}
