package com.n3k0chan.spotter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.di.ServiceLocator
import com.n3k0chan.spotter.ui.theme.SpotterText
import com.n3k0chan.spotter.ui.theme.SpotterTheme
import kotlinx.coroutines.launch

/**
 * Sheet modal de selección/creación de ejercicio. Reemplaza al AlertDialog antiguo.
 *
 * Tiene dos vistas:
 *  - Browse: lista de ejercicios (los predefinidos + los creados) con buscador
 *    y un acceso "Crear ejercicio" arriba.
 *  - Create: formulario completo con nombre, grupo muscular (selector con icono)
 *    y perfil de medida. Flecha atrás para volver a Browse.
 *
 * Cuando se crea un ejercicio nuevo se inserta en el catálogo y queda disponible
 * para futuras sesiones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    catalog: List<Exercise>,
    alreadyAdded: Set<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpotterTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(PickerMode.Browse) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        contentColor = c.text,
    ) {
        when (mode) {
            PickerMode.Browse -> BrowseView(
                catalog = catalog,
                alreadyAdded = alreadyAdded,
                onPick = onPick,
                onCreateNew = { mode = PickerMode.Create },
            )
            PickerMode.Create -> CreateView(
                onBack = { mode = PickerMode.Browse },
                onCreated = { exercise ->
                    onPick(exercise)
                },
            )
        }
    }
}

private enum class PickerMode { Browse, Create }

@Composable
private fun BrowseView(
    catalog: List<Exercise>,
    alreadyAdded: Set<Long>,
    onPick: (Exercise) -> Unit,
    onCreateNew: () -> Unit,
) {
    val c = SpotterTheme.colors
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 400.dp, max = 700.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Añadir ejercicio",
            style = SpotterText.title2,
            color = c.text,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar…", color = c.textFaint) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Acceso "+ Nuevo ejercicio"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.primarySoft)
                .clickable(onClick = onCreateNew)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Crear ejercicio nuevo", style = SpotterText.bodyMd, color = c.primarySoftText)
                Text(
                    "Se guardará en tu catálogo",
                    style = SpotterText.small,
                    color = c.primarySoftText.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "EJERCICIOS",
            style = SpotterText.caps,
            color = c.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )

        val filtered = remember(query, catalog) {
            if (query.isBlank()) catalog.sortedBy { it.name.lowercase() }
            else catalog.filter { it.name.contains(query, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
        }

        if (filtered.isEmpty()) {
            Text(
                "No hay ejercicios que coincidan.",
                style = SpotterText.body,
                color = c.textMuted,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.id }) { ex ->
                    ExerciseListRow(ex = ex, selected = ex.id in alreadyAdded, onClick = { onPick(ex) })
                    HorizontalDivider(color = c.border, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ExerciseListRow(ex: Exercise, selected: Boolean, onClick: () -> Unit) {
    val c = SpotterTheme.colors
    val profile = MeasurementProfile.fromNameOrDefault(ex.measurementProfile)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuscleGroupAvatar(group = MuscleGroup.from(ex.muscleGroup), size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.name, style = SpotterText.bodyMd, color = c.text, modifier = Modifier.weight(1f, fill = false))
                if (ex.isUserCreated) {
                    Spacer(Modifier.width(6.dp))
                    UserCreatedBadge()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(ex.muscleGroup ?: MuscleGroup.from(ex.muscleGroup).display)
                    append(" · ")
                    append(profile.display)
                },
                style = SpotterText.small,
                color = c.textMuted,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CreateView(
    onBack: () -> Unit,
    onCreated: (Exercise) -> Unit,
) {
    val c = SpotterTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(MuscleGroup.Otro) }
    var profile by remember { mutableStateOf(MeasurementProfile.Default) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 400.dp, max = 700.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpotterIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Volver",
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Crear ejercicio",
                style = SpotterText.title2,
                color = c.text,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("NOMBRE", style = SpotterText.caps, color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("p. ej. Curl alterno con mancuernas", color = c.textFaint) },
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text("GRUPO MUSCULAR", style = SpotterText.caps, color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        MuscleGroupPicker(
            selected = group,
            onSelect = { group = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text("CÓMO SE MIDE", style = SpotterText.caps, color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        ProfilePicker(
            selected = profile,
            onSelect = { profile = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        SpotterButton(
            text = "Guardar y añadir al entreno",
            full = true,
            enabled = name.isNotBlank() && !saving,
            onClick = {
                saving = true
                scope.launch {
                    val id = ServiceLocator.exercises.create(
                        name = name.trim(),
                        muscleGroup = group.display,
                        profile = profile,
                    )
                    val created = ServiceLocator.exercises.get(id)
                    if (created != null) onCreated(created)
                    saving = false
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Badge pequeño para indicar que el ejercicio lo creó el usuario. */
@Composable
fun UserCreatedBadge() {
    val c = SpotterTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.primarySoft)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            "Tuyo",
            style = SpotterText.caps.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = c.primarySoftText,
        )
    }
}

/** Selector de [MeasurementProfile] estilo dropdown con descripción. */
@Composable
private fun ProfilePicker(
    selected: MeasurementProfile,
    onSelect: (MeasurementProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SpotterTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceMuted)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selected.display, style = SpotterText.bodyMd, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(selected.description, style = SpotterText.small, color = c.textMuted)
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
            MeasurementProfile.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.display, style = SpotterText.bodyMd, color = c.text)
                            Text(p.description, style = SpotterText.small, color = c.textMuted)
                        }
                    },
                    onClick = {
                        onSelect(p)
                        expanded = false
                    },
                )
            }
        }
    }
}
