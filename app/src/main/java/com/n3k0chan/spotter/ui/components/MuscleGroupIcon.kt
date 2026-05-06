package com.n3k0chan.spotter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.n3k0chan.spotter.R
import com.n3k0chan.spotter.ui.theme.SpotterTheme

/**
 * Categorías canónicas que reconoce el helper.
 * Cada una tiene un drawable específico en res/drawable/ic_muscle_*.xml.
 * Los iconos son monocromos: el `tint` del Icon se aplica sobre el path.
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
        /**
         * Mapea texto libre del usuario a una categoría. Acepta variantes en español
         * comunes (bíceps/tríceps → Brazo, hombro → Pecho, femoral/cuádriceps/gemelo → Pierna,
         * core → Abdomen, etc.).
         */
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

/** "Avatar" cuadrado redondeado con el icono del grupo, estilo lo del Home. */
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

/** Atajo: directamente desde texto libre. */
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
