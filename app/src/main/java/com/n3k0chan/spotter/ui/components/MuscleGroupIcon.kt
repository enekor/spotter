package com.n3k0chan.spotter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme

/**
 * Categorías canónicas que reconoce el helper.
 * Cada una tiene un drawable específico en res/drawable/ic_muscle_*.xml.
 */
enum class MuscleGroup(
    val display: String,
    @DrawableRes val iconRes: Int,
) {
    Cardio("Cardio", R.drawable.ic_muscle_cardio),
    Abdomen("Abdomen", R.drawable.ic_muscle_abs),
    Brazo("Brazo", R.drawable.ic_muscle_arm),
    Pecho("Pecho", R.drawable.ic_muscle_chest),
    Espalda("Espalda", R.drawable.ic_muscle_back),
    Pierna("Pierna", R.drawable.ic_muscle_leg),
    Otro("Otro", R.drawable.ic_muscle_other);

    companion object {
        fun from(raw: String?): MuscleGroup {
            val s = raw?.trim()?.lowercase()
                ?.replace(Regex("[áàä]"), "a")
                ?.replace(Regex("[éèë]"), "e")
                ?.replace(Regex("[íìï]"), "i")
                ?.replace(Regex("[óòö]"), "o")
                ?.replace(Regex("[úùü]"), "u")
                ?: return Otro
            return when {
                s.isBlank() -> Otro
                s.contains("cardio") || s.contains("aerob") || s.contains("hiit") ||
                    s.contains("correr") || s.contains("bici") -> Cardio
                s.contains("abdomen") || s.contains("abdo") || s.contains("core") ||
                    s.contains("oblic") -> Abdomen
                s.contains("brazo") || s.contains("biceps") || s.contains("triceps") ||
                    s.contains("antebrazo") -> Brazo
                s.contains("pecho") || s.contains("pectoral") || s.contains("hombro") -> Pecho
                s.contains("espalda") || s.contains("dorsal") || s.contains("trapecio") ||
                    s.contains("lumbar") -> Espalda
                s.contains("pierna") || s.contains("cuadricep") || s.contains("femoral") ||
                    s.contains("isquio") || s.contains("gluteo") || s.contains("gemelo") ||
                    s.contains("pantorrilla") || s.contains("aductor") || s.contains("abductor") -> Pierna
                else -> Otro
            }
        }
    }
}

/** "Avatar" cuadrado redondeado con el icono del grupo. */
@Composable
fun MuscleGroupAvatar(
    group: MuscleGroup,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color? = null,
    tint: Color? = null,
) {
    val c = SpotterTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(background ?: c.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = group.iconRes),
            contentDescription = group.display,
            tint = tint ?: c.textMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun MuscleGroupAvatar(
    rawGroup: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    MuscleGroupAvatar(
        group = MuscleGroup.from(rawGroup),
        modifier = modifier,
        size = size,
        iconSize = iconSize,
    )
}

/**
 * Selector tipo dropdown con icono visible. Cerrado: muestra avatar + nombre.
 * Abierto: lista los 7 grupos canónicos con icono. Evita escribir mal y
 * garantiza que siempre acabamos con un valor que tiene icono asignado.
 */
@Composable
fun MuscleGroupPicker(
    selected: MuscleGroup,
    onSelect: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceMuted)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MuscleGroupAvatar(group = selected, size = 32.dp, iconSize = 18.dp)
            Spacer(Modifier.size(10.dp))
            Text(
                text = selected.display,
                style = SpotterText.bodyMd,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = c.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MuscleGroup.entries.forEach { g ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MuscleGroupAvatar(group = g, size = 28.dp, iconSize = 16.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(g.display)
                        }
                    },
                    onClick = {
                        onSelect(g)
                        expanded = false
                    },
                )
            }
        }
    }
}
