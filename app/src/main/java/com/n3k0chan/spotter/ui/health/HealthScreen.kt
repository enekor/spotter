package com.n3k0chan.spotter.ui.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.n3k0chan.spotter.health.HealthConnectRepository
import com.n3k0chan.spotter.health.HealthSnapshot
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    vm: HealthViewModel = viewModel(factory = HealthViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }

    // Pre-warmup del cliente: en algunos dispositivos (sobre todo Android 14+
    // recién instalado) el contract de permisos no responde si HealthConnectClient
    // aún no ha sido instanciado.
    LaunchedEffect(state.availability) {
        if (state.availability == HealthConnectRepository.Availability.Available) {
            runCatching { HealthConnectClient.getOrCreate(ctx) }
        }
    }

    // Si el usuario va a Health Connect a conceder permisos manualmente, al volver
    // refrescamos el estado automáticamente.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshAvailability()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { vm.onPermissionsResult() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Salud") },
                actions = {
                    if (state.availability == HealthConnectRepository.Availability.Available && state.hasPermissions) {
                        IconButton(onClick = { vm.loadSnapshot() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refrescar")
                        }
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
            when (state.availability) {
                HealthConnectRepository.Availability.Unsupported -> UnsupportedState()
                HealthConnectRepository.Availability.NeedsProvider -> NeedsProviderState(
                    onInstall = { openHealthConnectInPlayStore(ctx) },
                )
                HealthConnectRepository.Availability.Available -> {
                    if (!state.hasPermissions) {
                        PermissionsState(
                            errorMessage = launchError,
                            onGrant = {
                                launchError = null
                                runCatching { permissionLauncher.launch(vm.readPermissions) }
                                    .onFailure {
                                        launchError = "No se pudo abrir el diálogo de permisos. " +
                                            "Abre Health Connect manualmente y concede los permisos a Spotter."
                                    }
                            },
                            onOpenHealthConnect = { openHealthConnectApp(ctx) },
                            onRecheck = { vm.refreshAvailability() },
                        )
                    } else {
                        SnapshotGrid(
                            snapshot = state.snapshot,
                            loading = state.loading,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnsupportedState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Health Connect no disponible", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Tu dispositivo no soporta Health Connect.")
        }
    }
}

@Composable
private fun NeedsProviderState(onInstall: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Falta la app de Health Connect", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "En Android 13 y anteriores, Health Connect se instala desde Play Store. " +
                    "En Android 14+ ya viene integrado.",
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onInstall) { Text("Instalar Health Connect") }
        }
    }
}

@Composable
private fun PermissionsState(
    errorMessage: String?,
    onGrant: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onRecheck: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Conectar con Health Connect", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Spotter solo lee tus datos: pasos, distancia, calorías activas, frecuencia cardíaca, " +
                    "FC en reposo, sueño, peso y % grasa. Nunca escribe ni envía nada fuera del dispositivo " +
                    "salvo que tú lo pidas explícitamente al asistente.",
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text("Conceder permisos de lectura")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onOpenHealthConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir Health Connect")
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
                    Text("Comprobar de nuevo")
                }
            }
        }
    }
}

@Composable
private fun SnapshotGrid(snapshot: HealthSnapshot, loading: Boolean) {
    if (loading && snapshot.isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (snapshot.isEmpty) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sin datos por ahora", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ningún proveedor (smartwatch, báscula, app de running…) ha enviado datos hoy a Health Connect.",
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        snapshot.steps?.let { item { MetricTile("Pasos", "$it") } }
        snapshot.activeKcal?.let { item { MetricTile("Cal. activas", "${it.toInt()} kcal") } }
        snapshot.distanceMeters?.let { item { MetricTile("Distancia", "%.1f km".format(it / 1000)) } }
        snapshot.avgHeartRateBpm?.let { item { MetricTile("FC media", "${it.toInt()} bpm") } }
        snapshot.restingHeartRateBpm?.let { item { MetricTile("FC reposo", "$it bpm") } }
        snapshot.sleepLastNight?.let { item { MetricTile("Sueño anoche", formatDuration(it)) } }
        snapshot.weightKg?.let { item { MetricTile("Peso", "%.1f kg".format(it)) } }
        snapshot.bodyFatPct?.let { item { MetricTile("Grasa", "%.1f %%".format(it)) } }
    }
}

@Composable
private fun MetricTile(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return "${h}h ${m}min"
}

/**
 * Abre la pantalla de gestión de permisos de Health Connect para esta app.
 * - Android 14+ (API 34+): integrado en Settings del sistema.
 * - Android 13 y anteriores: app aparte (com.google.android.apps.healthdata).
 * Si nada funciona cae a abrir Health Connect a secas.
 */
private fun openHealthConnectApp(context: android.content.Context) {
    val candidates = listOf(
        // Android 14+: pide gestión de permisos para nuestra app
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        },
        // Android 13 y anteriores: settings de la app de Health Connect
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        // Fallback final: launcher de la app de Health Connect
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata"),
    )
    for (intent in candidates) {
        if (intent == null) continue
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent) }.onSuccess { return }
        }
    }
}

private fun openHealthConnectInPlayStore(context: android.content.Context) {
    val pkg = "com.google.android.apps.healthdata"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }
}
