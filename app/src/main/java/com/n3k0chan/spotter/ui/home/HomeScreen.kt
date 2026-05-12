package com.n3k0chan.spotter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.ui.components.SpotterButton
import com.n3k0chan.spotter.ui.components.SpotterButtonVariant
import com.n3k0chan.spotter.ui.components.SpotterCard
import com.n3k0chan.spotter.ui.components.SpotterIconButton
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme

@Composable
fun HomeScreen(
    onStartFreeWorkout: (Long) -> Unit,
    onPickTemplate: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val weeks by vm.weeksStreak.collectAsStateWithLifecycle()
    val totalDays by vm.totalDays.collectAsStateWithLifecycle()
    val greeting by vm.greeting.collectAsStateWithLifecycle()
    val templates by vm.templatesList.collectAsStateWithLifecycle()
    val c = SpotterTheme.colors

    Scaffold(
        containerColor = c.bg,
        topBar = {
            com.n3k0chan.spotter.ui.components.SpotterTopBar(
                title = "Spotter",
                trailing = {
                    SpotterIconButton(
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        contentDescription = "Ajustes",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StreakCard(weeksStreak = weeks, totalDays = totalDays, motivational = greeting)

            SpotterButton(
                text = "Entreno libre",
                onClick = { vm.startFreeWorkout(onStartFreeWorkout) },
                leading = Icons.Filled.PlayArrow,
                full = true,
            )
            SpotterButton(
                text = "Empezar plantilla",
                onClick = onPickTemplate,
                leading = Icons.Filled.FitnessCenter,
                variant = SpotterButtonVariant.Outlined,
                full = true,
            )

            SectionHeader("PLANTILLAS RECIENTES")
            if (templates.isEmpty()) {
                Text(
                    "Aún no tienes plantillas. Crea una para reutilizar tus rutinas.",
                    style = SpotterText.body,
                    color = c.textMuted,
                )
            } else {
                templates.take(3).forEach { tpl ->
                    TemplateRow(
                        name = tpl.template.name,
                        detail = "${tpl.items.size} ejercicios",
                        onClick = { vm.startFromTemplate(tpl.template.id, onStartFreeWorkout) },
                    )
                }
            }

            AiCard(onClick = onOpenChat)
        }
    }
}

@Composable
private fun StreakCard(weeksStreak: Int, totalDays: Int, motivational: String?) {
    val c = SpotterTheme.colors
    SpotterCard(padding = 20.dp) {
        Column {
            Text("Buenos días", style = SpotterText.small, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                StatColumn(
                    value = "$weeksStreak",
                    label = if (weeksStreak == 1) "semana seguida" else "semanas seguidas",
                    accent = true,
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    value = "$totalDays",
                    label = if (totalDays == 1) "día total" else "días totales",
                    accent = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (weeksStreak > 0) "Racha activa" else "Sin racha esta semana",
                    style = SpotterText.smallMd,
                    color = c.primary,
                )
            }
            if (!motivational.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = c.border, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                Text(motivational, style = SpotterText.body, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, accent: Boolean, modifier: Modifier = Modifier) {
    val c = SpotterTheme.colors
    Column(modifier = modifier) {
        Text(
            text = value,
            style = SpotterText.numL.copy(fontSize = 44.sp),
            color = if (accent) c.text else c.textMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = SpotterText.small, color = c.textMuted)
    }
}

@Composable
private fun SectionHeader(text: String) {
    val c = SpotterTheme.colors
    Text(
        text,
        style = SpotterText.caps,
        color = c.textMuted,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
private fun TemplateRow(name: String, detail: String, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    SpotterCard(padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = c.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = SpotterText.bodyMd, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = SpotterText.small, color = c.textMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AiCard(onClick: () -> Unit) {
    val c = SpotterTheme.colors
    SpotterCard(
        padding = 14.dp,
        background = c.primarySoft,
        border = c.primarySoft,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hablar con el asistente",
                    style = SpotterText.bodyMd,
                    color = c.primarySoftText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pregúntale por tu progreso o tu próxima sesión",
                    style = SpotterText.small,
                    color = c.primarySoftText.copy(alpha = 0.8f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = c.primarySoftText.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
