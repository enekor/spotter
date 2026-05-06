package com.n3k0chan.spotter.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.n3k0chan.spotter.timer.RestTimerController
import com.n3k0chan.spotter.timer.RestTimerService
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme

/**
 * Barra naranja con cuenta atrás del descanso.
 * Diseño: bg primary, contenido onPrimary, "pills" semi-transparentes para botones.
 */
@Composable
fun RestTimerBar(modifier: Modifier = Modifier) {
    val state by RestTimerController.state.collectAsStateWithLifecycle()
    if (!state.isRunning && state.totalSeconds == 0) return
    val ctx = LocalContext.current
    val c = SpotterTheme.colors
    val pct = if (state.totalSeconds > 0)
        (state.totalSeconds - state.remainingSeconds).toFloat() / state.totalSeconds
    else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.primary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = c.onPrimary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (state.isRunning) "Descanso" else "Descanso terminado",
                style = SpotterText.smallMd,
                color = c.onPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatMmSs(state.remainingSeconds),
                style = SpotterText.numM,
                color = c.onPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onPrimary),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isRunning) {
                RestPillButton(icon = Icons.Filled.Add, label = "+15s") { RestTimerService.add15(ctx) }
                RestPillButton(icon = Icons.Filled.SkipNext, label = "Saltar") { RestTimerService.skip(ctx) }
            } else {
                RestPillButton(icon = Icons.Filled.Close, label = "Cerrar") { RestTimerService.skip(ctx) }
            }
        }
    }
}

@Composable
private fun RestPillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = SpotterText.smallMd, color = c.onPrimary)
    }
}

internal fun formatMmSs(s: Int): String {
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}
