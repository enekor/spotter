package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3k0chan.spotter.timer.RestTimerController
import com.n3k0chan.spotter.timer.RestTimerService

@Composable
fun RestTimerBar(modifier: Modifier = Modifier) {
    val state by RestTimerController.state.collectAsStateWithLifecycle()
    if (!state.isRunning && state.totalSeconds == 0) return
    val ctx = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.isRunning) "Descanso · ${formatMmSs(state.remainingSeconds)}"
                    else "Descanso terminado",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.isRunning) {
                    OutlinedButton(onClick = { RestTimerService.add15(ctx) }) { Text("+15s") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { RestTimerService.skip(ctx) }) { Text("Saltar") }
                } else {
                    TextButton(onClick = { RestTimerService.skip(ctx) }) { Text("Cerrar") }
                }
            }
            if (state.isRunning) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatMmSs(s: Int): String {
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}
