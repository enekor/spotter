package com.n3k0chan.spotter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.health.HealthSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartFreeWorkout: (Long) -> Unit,
    onPickTemplate: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val streak by vm.streak.collectAsStateWithLifecycle()
    val greeting by vm.greeting.collectAsStateWithLifecycle()
    val templates by vm.templatesList.collectAsStateWithLifecycle()
    val health by vm.healthSummary.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = streakText(streak),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = greeting ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            HealthSummaryCard(snapshot = health, onClick = onOpenHealth)

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.startFreeWorkout(onStartFreeWorkout) },
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_start_free))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickTemplate,
            ) { Text(stringResource(R.string.home_start_template)) }

            Spacer(Modifier.height(24.dp))
            Text("Plantillas recientes", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (templates.isEmpty()) {
                Text(
                    stringResource(R.string.templates_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates.take(5), key = { it.template.id }) { tpl ->
                        Card(
                            onClick = { vm.startFromTemplate(tpl.template.id, onStartFreeWorkout) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tpl.template.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tpl.items.size} ejercicios",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Hablar con el asistente")
            }
        }
    }
}

@Composable
private fun HealthSummaryCard(snapshot: HealthSnapshot?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MonitorHeart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salud", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Ver todo →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (snapshot == null || snapshot.isEmpty) {
                Text(
                    "Sin datos. Conecta Health Connect en la pestaña Salud.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    snapshot.steps?.let { MiniStat("Pasos", "$it", Modifier.weight(1f)) }
                    snapshot.sleepLastNight?.let {
                        MiniStat(
                            "Sueño",
                            "${it.toHours()}h ${it.toMinutes() % 60}m",
                            Modifier.weight(1f),
                        )
                    }
                    snapshot.restingHeartRateBpm?.let {
                        MiniStat("FC reposo", "$it bpm", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun streakText(streak: Int): String {
    val days = stringResource(R.string.home_days)
    return if (streak <= 0) stringResource(R.string.home_no_streak)
    else "${stringResource(R.string.home_streak)}: $streak $days"
}
