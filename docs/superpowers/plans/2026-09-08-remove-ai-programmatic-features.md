# Eliminar IA + comparación y sugerencia programáticas — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retirar toda la funcionalidad basada en IA (Groq/LLM) de Spotter y sustituir la comparación post-entreno y la sugerencia de siguiente ejercicio por lógica programática local.

**Architecture:** Dos helpers puros y testeables (`WorkoutComparator`, `NextExerciseSuggester`) encapsulan la lógica nueva; `WorkoutViewModel` los orquesta. El resto es eliminación de código (paquete `ai/`, chat, análisis de peso, import de Health Connect, ajustes de API key) y limpieza de dependencias de red.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, kotlinx-serialization, JUnit4 (tests JVM).

## Global Constraints

- **El desarrollador ejecuta personalmente los comandos de build (`./gradlew …`), test y `git`.** El agente NO ejecuta builds ni git de escritura. En cada paso "Verificar compilación", "Ejecutar tests" o "Commit", el agente PIDE al usuario que ejecute el comando indicado y ESPERA su confirmación antes de continuar. El agente puede usar `Grep`/`Read` (solo lectura) para verificar el estado del código.
- Package base: `com.n3k0chan.spotter`. Source: `app/src/main/java/com/n3k0chan/spotter/`. Tests JVM: `app/src/test/java/com/n3k0chan/spotter/`.
- **No cambiar el esquema de la BD.** Se reutiliza la columna `Workout.aiSummaryJson` sin migración.
- **Mantener** el permiso `INTERNET` (lo usa el backup de Drive) y `kotlinx-serialization`.
- **Mantener** `data/health/HealthConnectManager` y la lectura de métricas en `WorkoutViewModel.persistWorkout` (no es IA). Solo se elimina la *importación de sesiones*.
- Convención de commits (Conventional Commits): `<type>(<scope>): <description>`, imperativo, minúscula, sin punto final, sin líneas de atribución a IA.

---

## Resumen de archivos afectados

**Nuevos:**
- `ui/workout/WorkoutComparison.kt` — modelos serializables `WorkoutComparison` / `ComparisonExercise`.
- `ui/workout/WorkoutComparator.kt` — cálculo de la comparación post-entreno.
- `ui/workout/NextExerciseSuggester.kt` — selección del siguiente ejercicio.
- `app/src/test/java/com/n3k0chan/spotter/workout/WorkoutComparatorTest.kt`
- `app/src/test/java/com/n3k0chan/spotter/workout/NextExerciseSuggesterTest.kt`

**Modificados:**
- `ui/workout/WorkoutViewModel.kt` — orquestación de comparador y sugeridor; se quita IA.
- `ui/workout/WorkoutScreen.kt` — diálogo post-entreno con datos locales; tarjeta fantasma; quitar menú "Sugerencia IA".
- `ui/history/WorkoutDetailScreen.kt` — etiquetas "IA" → "Comparación".
- `data/db/dao/WorkoutDao.kt` + `data/repository/WorkoutRepository.kt` — query de recuentos por ejercicio.
- `ui/nav/Routes.kt`, `ui/nav/SpotterNavGraph.kt` — quitar rutas Chat y Health.
- `ui/home/HomeScreen.kt` + `ui/home/HomeViewModel.kt`, `ui/workout/WorkoutHubScreen.kt`, `ui/history/HistoryScreen.kt`, `ui/stats/StatsScreen.kt`, `ui/weight/WeightScreen.kt` + `ui/weight/WeightViewModel.kt` — quitar `onOpenChat`, botones ✨ y análisis IA.
- `ui/settings/SettingsScreen.kt`, `data/prefs/SettingsRepository.kt` — quitar API key, modelo, `ChatHistoryWindow`.
- `app/build.gradle.kts`, `local.properties.example`, `README.md`.

**Eliminados:**
- `ai/` completo (`GroqClient.kt`, `GroqService.kt`, `GroqModels.kt`, `Prompts.kt`).
- `ui/chat/ChatScreen.kt` (+ ViewModel de chat si existe).
- `ui/health/HealthScreen.kt`, `ui/health/HealthViewModel.kt`.

---

## Task 1: Comparador programático (helper puro + modelos)

**Files:**
- Create: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparison.kt`
- Create: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparator.kt`
- Test: `app/src/test/java/com/n3k0chan/spotter/workout/WorkoutComparatorTest.kt`

**Interfaces:**
- Produces:
  - `data class WorkoutComparison(val summary: String, val exercises: List<ComparisonExercise>)`
  - `data class ComparisonExercise(val name: String, val markdown: String)`
  - `data class ExerciseComparisonInput(val name: String, val profile: MeasurementProfile, val todaySets: List<WorkoutSet>, val historySets: List<WorkoutSet>)`
  - `object WorkoutComparator { fun compare(inputs: List<ExerciseComparisonInput>): WorkoutComparison; fun isPr(input: ExerciseComparisonInput): Boolean; fun volumeOf(sets: List<WorkoutSet>, profile: MeasurementProfile): Double; fun bestSet(sets: List<WorkoutSet>, profile: MeasurementProfile): WorkoutSet? }`
  - Nota: `historySets` = series de entrenos anteriores (excluye hoy), ordenadas de más reciente a más antigua.

- [ ] **Step 1: Escribir el test que falla**

Crear `app/src/test/java/com/n3k0chan/spotter/workout/WorkoutComparatorTest.kt`:

```kotlin
package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutComparatorTest {

    private fun wr(workoutId: Long, weight: Double, reps: Int) = WorkoutSet(
        workoutId = workoutId, exerciseId = 1, orderIndex = 0, setNumber = 1,
        weightKg = weight, reps = reps,
    )

    @Test
    fun `volume for weightReps is sum of weight times reps`() {
        val sets = listOf(wr(1, 100.0, 5), wr(1, 100.0, 5))
        assertEquals(1000.0, WorkoutComparator.volumeOf(sets, MeasurementProfile.WeightReps), 0.0)
    }

    @Test
    fun `detects PR when today beats history`() {
        val input = ExerciseComparisonInput(
            name = "Press", profile = MeasurementProfile.WeightReps,
            todaySets = listOf(wr(2, 105.0, 5)),
            historySets = listOf(wr(1, 100.0, 5)),
        )
        assertTrue(WorkoutComparator.isPr(input))
    }

    @Test
    fun `no PR when today equals history`() {
        val input = ExerciseComparisonInput(
            name = "Press", profile = MeasurementProfile.WeightReps,
            todaySets = listOf(wr(2, 100.0, 5)),
            historySets = listOf(wr(1, 100.0, 5)),
        )
        assertFalse(WorkoutComparator.isPr(input))
    }

    @Test
    fun `no previous data yields explanatory verdict`() {
        val comparison = WorkoutComparator.compare(
            listOf(
                ExerciseComparisonInput(
                    "Press", MeasurementProfile.WeightReps,
                    todaySets = listOf(wr(2, 100.0, 5)), historySets = emptyList(),
                ),
            ),
        )
        assertEquals(1, comparison.exercises.size)
        assertTrue(comparison.exercises[0].markdown.contains("Sin datos previos"))
    }
}
```

- [ ] **Step 2: Pedir al usuario que ejecute los tests y confirme que fallan**

Pedir: ejecutar `./gradlew :app:testDebugUnitTest --tests "com.n3k0chan.spotter.ui.workout.WorkoutComparatorTest"`
Esperado: FALLA de compilación (aún no existen `WorkoutComparator` ni los modelos). Esperar confirmación del usuario.

- [ ] **Step 3: Crear los modelos serializables**

Crear `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparison.kt`:

```kotlin
package com.n3k0chan.spotter.ui.workout

import kotlinx.serialization.Serializable

/**
 * Resultado de la comparación programática de un entreno. Se persiste como JSON
 * en Workout.aiSummaryJson (columna reutilizada, sin migración) y se muestra en
 * el diálogo post-entreno y en el detalle del historial. Las claves JSON
 * (summary/exercises/name/markdown) se mantienen para leer resúmenes antiguos.
 */
@Serializable
data class WorkoutComparison(
    val summary: String,
    val exercises: List<ComparisonExercise> = emptyList(),
)

@Serializable
data class ComparisonExercise(
    val name: String,
    /** Texto plano (líneas separadas por \n); el diálogo lo pinta como Text. */
    val markdown: String,
)
```

- [ ] **Step 4: Implementar el comparador**

Crear `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparator.kt`:

```kotlin
package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.measurement.MeasurementProfile
import com.n3k0chan.spotter.data.measurement.formatShort

/** Entrada por ejercicio para el comparador. */
data class ExerciseComparisonInput(
    val name: String,
    val profile: MeasurementProfile,
    /** Series registradas hoy para este ejercicio. */
    val todaySets: List<WorkoutSet>,
    /** Series de entrenos anteriores (excluye hoy), más reciente primero. */
    val historySets: List<WorkoutSet>,
)

/** Comparación 100% local del entreno frente al histórico. Sin red, sin LLM. */
object WorkoutComparator {

    fun compare(inputs: List<ExerciseComparisonInput>): WorkoutComparison {
        val exercises = inputs.mapNotNull { input ->
            if (input.todaySets.isEmpty()) null
            else ComparisonExercise(input.name, verdictFor(input))
        }
        val totalSets = inputs.sumOf { it.todaySets.size }
        val prCount = inputs.count { isPr(it) }
        val summary = buildString {
            append("${exercises.size} ejercicios · $totalSets series")
            if (prCount > 0) append(" · $prCount PR${if (prCount > 1) "s" else ""}")
        }
        return WorkoutComparison(summary = summary, exercises = exercises)
    }

    private fun verdictFor(input: ExerciseComparisonInput): String {
        val profile = input.profile
        val todayVol = volumeOf(input.todaySets, profile)
        val lines = mutableListOf<String>()
        lines += "Hoy: ${input.todaySets.size} series · ${formatVolume(todayVol, profile)}"

        val histBest = bestSet(input.historySets, profile)
        val todayBest = bestSet(input.todaySets, profile)
        if (histBest == null || todayBest == null) {
            lines += "Sin datos previos para comparar"
            return lines.joinToString("\n")
        }

        lines += if (isPr(input)) {
            "Nuevo PR: ${todayBest.formatShort(profile)} (antes ${histBest.formatShort(profile)})"
        } else {
            "Mejor hoy: ${todayBest.formatShort(profile)} · PR: ${histBest.formatShort(profile)}"
        }

        val lastVol = volumeOf(lastSession(input.historySets), profile)
        if (lastVol > 0) {
            val pct = ((todayVol - lastVol) / lastVol * 100).toInt()
            val sign = if (pct >= 0) "+" else ""
            lines += "Volumen $sign$pct% vs última sesión"
        }
        return lines.joinToString("\n")
    }

    fun isPr(input: ExerciseComparisonInput): Boolean {
        val profile = input.profile
        val histBest = bestSet(input.historySets, profile) ?: return false
        val todayBest = bestSet(input.todaySets, profile) ?: return false
        return metric(todayBest, profile) > metric(histBest, profile)
    }

    fun bestSet(sets: List<WorkoutSet>, profile: MeasurementProfile): WorkoutSet? =
        sets.maxByOrNull { metric(it, profile) }

    fun volumeOf(sets: List<WorkoutSet>, profile: MeasurementProfile): Double = when (profile) {
        MeasurementProfile.WeightReps -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
        MeasurementProfile.Reps -> sets.sumOf { (it.reps ?: 0).toDouble() }
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration ->
            sets.sumOf { (it.durationSeconds ?: 0).toDouble() }
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline -> sets.sumOf { it.distanceMeters ?: 0.0 }
    }

    /** Métrica principal comparable de una serie según el perfil. */
    private fun metric(s: WorkoutSet, profile: MeasurementProfile): Double = when (profile) {
        // Prioriza peso; a igualdad, más reps.
        MeasurementProfile.WeightReps -> (s.weightKg ?: 0.0) * 1000 + (s.reps ?: 0)
        MeasurementProfile.Reps -> (s.reps ?: 0).toDouble()
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration ->
            (s.durationSeconds ?: 0).toDouble()
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline -> s.distanceMeters ?: 0.0
    }

    /** Series de la sesión histórica más reciente (primer workoutId de la lista). */
    private fun lastSession(historySets: List<WorkoutSet>): List<WorkoutSet> {
        val firstWorkoutId = historySets.firstOrNull()?.workoutId ?: return emptyList()
        return historySets.filter { it.workoutId == firstWorkoutId }
    }

    private fun formatVolume(volume: Double, profile: MeasurementProfile): String = when (profile) {
        MeasurementProfile.WeightReps -> "${volume.toInt()} kg·rep"
        MeasurementProfile.Reps -> "${volume.toInt()} reps"
        MeasurementProfile.Duration, MeasurementProfile.WeightDuration -> {
            val s = volume.toInt(); "%d:%02d min".format(s / 60, s % 60)
        }
        MeasurementProfile.DistanceTime,
        MeasurementProfile.CardioMachine,
        MeasurementProfile.TreadmillIncline ->
            if (volume >= 1000) "%.2f km".format(volume / 1000) else "${volume.toInt()} m"
    }
}
```

- [ ] **Step 5: Pedir al usuario que ejecute los tests y confirme que pasan**

Pedir: `./gradlew :app:testDebugUnitTest --tests "com.n3k0chan.spotter.ui.workout.WorkoutComparatorTest"`
Esperado: PASS (4 tests). Esperar confirmación.

- [ ] **Step 6: Commit (el usuario lo ejecuta)**

Pedir al usuario que ejecute:
```bash
git add app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparison.kt \
        app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutComparator.kt \
        app/src/test/java/com/n3k0chan/spotter/workout/WorkoutComparatorTest.kt
git commit -m "feat(workout): add programmatic post-session comparator"
```

---

## Task 2: Cablear la comparación en el flujo de terminar entreno

**Files:**
- Modify: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutViewModel.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutScreen.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/ui/history/WorkoutDetailScreen.kt`

**Interfaces:**
- Consumes: `WorkoutComparator.compare(...)`, `WorkoutComparison`, `ExerciseComparisonInput` (Task 1).
- Produces: `WorkoutUiState.finishedComparison: WorkoutComparison?` (sustituye a `finishedSummary`).

- [ ] **Step 1: Reemplazar los modelos IA y el estado en `WorkoutViewModel.kt`**

Borrar las clases `AiSummaryResponse` y `AiSummaryExercise` (líneas 26-36) y sus imports de serialización si quedan sin uso salvo `Json`. En `WorkoutUiState`, sustituir:
- `val finishedSummary: AiSummaryResponse? = null,` → `val finishedComparison: WorkoutComparison? = null,`
- Eliminar `val finishedLoading: Boolean = false,` (el cálculo es instantáneo).

Mantener `showPostFinish`. Actualizar el import: quitar `import com.n3k0chan.spotter.ai.GroqClient` y `import com.n3k0chan.spotter.ai.Prompts` (se dejarán de usar al terminar este task y el 3).

- [ ] **Step 2: Reescribir `finish()` para usar el comparador**

Sustituir el cuerpo de `finish(...)` (líneas ~215-267) por una versión sin IA:

```kotlin
fun finish(chosenStartedAt: Long, backupAfterFinish: Boolean, onDone: () -> Unit) {
    viewModelScope.launch {
        persistWorkout(chosenStartedAt, backupAfterFinish)

        val full = workouts.get(workoutId)
        if (full == null) {
            onDone()
            return@launch
        }

        val comparison = buildComparison(full)
        // Persistir para verlo luego en el detalle (columna reutilizada).
        workouts.get(workoutId)?.workout?.let { w ->
            runCatching {
                val json = Json.encodeToString(WorkoutComparison.serializer(), comparison)
                workouts.update(w.copy(aiSummaryJson = json))
            }
        }
        _state.update { it.copy(showPostFinish = true, finishedComparison = comparison) }
    }
}

private suspend fun buildComparison(full: WorkoutWithSets): WorkoutComparison {
    val byExercise = full.sets.groupBy { it.exercise.id }
    val inputs = byExercise.map { (exerciseId, setsWithEx) ->
        val exercise = setsWithEx.first().exercise
        val profile = com.n3k0chan.spotter.data.measurement.MeasurementProfile
            .fromNameOrDefault(exercise.measurementProfile)
        val todaySets = setsWithEx.map { it.set }
        val history = workouts.recentSetsFor(exerciseId, limit = 30)
            .filter { it.workoutId != workoutId }
        ExerciseComparisonInput(exercise.name, profile, todaySets, history)
    }
    return WorkoutComparator.compare(inputs)
}
```

Nota: `Json` ya está importado (`kotlinx.serialization.json.Json`). Asegurar que `WorkoutWithSets` está importado (ya lo está).

- [ ] **Step 3: Actualizar `WorkoutScreen.kt` — diálogo post-entreno**

En `WorkoutScreen` (bloque `if (state.showPostFinish)`, líneas ~201-207):

```kotlin
if (state.showPostFinish) {
    PostFinishSummaryDialog(
        summary = state.finishedComparison,
        onDismiss = onFinished,
    )
}
```

Cambiar la firma y cuerpo de `PostFinishSummaryDialog` (líneas ~774-873):
- Quitar el parámetro `loading: Boolean` y todo el bloque `if (loading) { … } else if (summary != null)`; dejar solo la rama `summary != null` y el `else` (mensaje motivacional).
- Cambiar el tipo del parámetro `summary: AiSummaryResponse?` → `summary: WorkoutComparison?`.
- Sustituir el import `import com.n3k0chan.spotter.ui.workout.AiSummaryResponse` (línea 41) por `import com.n3k0chan.spotter.ui.workout.WorkoutComparison` (o quitarlo, al estar en el mismo package).
- Cambiar el icono decorativo `Icons.Filled.AutoAwesome` de las tarjetas del pager por `Icons.Filled.TrendingUp`.
- `onDismiss` ya no depende de `loading`: `onDismissRequest = { onDismiss() }`.

- [ ] **Step 4: Actualizar `WorkoutDetailScreen.kt` — etiquetas**

Leer el archivo y localizar dónde se deserializa/muestra `aiSummaryJson` (buscar `AiSummaryResponse`, "IA", "Resumen"). Cambiar:
- El tipo `AiSummaryResponse` → `WorkoutComparison` en el `decodeFromString`.
- Los textos de UI "IA" / "Resumen IA" → "Comparación".
- Cualquier icono `AutoAwesome` decorativo → `TrendingUp`.

Usar `Grep` (solo lectura) para localizar todas las apariciones antes de editar:
`Grep pattern="AiSummaryResponse|AutoAwesome|Resumen IA|\\bIA\\b" path="app/src/main/java/com/n3k0chan/spotter/ui/history/WorkoutDetailScreen.kt"`.

- [ ] **Step 5: Verificar compilación (el usuario la ejecuta)**

Pedir: `./gradlew :app:compileDebugKotlin`
Esperado: compila. Nota: `fetchSuggestion`/`clearSuggestion` todavía usan `GroqClient`/`Prompts` y el paquete `ai/` sigue presente, así que compila. Si el compilador se queja de `finishedSummary`/`finishedLoading` en `WorkoutScreen`, corregir las referencias restantes. Esperar confirmación.

- [ ] **Step 6: Commit (el usuario lo ejecuta)**

```bash
git add app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutViewModel.kt \
        app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutScreen.kt \
        app/src/main/java/com/n3k0chan/spotter/ui/history/WorkoutDetailScreen.kt
git commit -m "feat(workout): use programmatic comparison on finish"
```

---

## Task 3: Sugerencia de siguiente ejercicio (helper + VM + tarjeta fantasma)

**Files:**
- Create: `app/src/main/java/com/n3k0chan/spotter/ui/workout/NextExerciseSuggester.kt`
- Test: `app/src/test/java/com/n3k0chan/spotter/workout/NextExerciseSuggesterTest.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/data/db/dao/WorkoutDao.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/data/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutViewModel.kt`
- Modify: `app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutScreen.kt`

**Interfaces:**
- Consumes: `MuscleGroup.from(...)` (en `ui/components/MuscleGroupIcon.kt`), `Exercise`.
- Produces:
  - `object NextExerciseSuggester { fun suggest(sessionExercises: List<Exercise>, catalog: List<Exercise>, trainingCounts: Map<Long, Int>): Long? }`
  - `WorkoutRepository.exerciseTrainingCounts(): Map<Long, Int>`
  - `WorkoutUiState.suggestedExerciseId: Long?`, `WorkoutUiState.suggestionDismissed: Boolean`
  - `WorkoutViewModel.dismissSuggestion()`

- [ ] **Step 1: Escribir el test del sugeridor (falla)**

Crear `app/src/test/java/com/n3k0chan/spotter/workout/NextExerciseSuggesterTest.kt`:

```kotlin
package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextExerciseSuggesterTest {

    private fun ex(id: Long, group: String?) = Exercise(id = id, name = "e$id", muscleGroup = group)

    @Test
    fun `returns null with fewer than two session exercises`() {
        val result = NextExerciseSuggester.suggest(
            sessionExercises = listOf(ex(1, "Pecho")),
            catalog = listOf(ex(1, "Pecho"), ex(2, "Pecho")),
            trainingCounts = emptyMap(),
        )
        assertNull(result)
    }

    @Test
    fun `suggests most trained from predominant group not already in session`() {
        val session = listOf(ex(1, "Pecho"), ex(2, "Pecho"))
        val catalog = session + listOf(ex(3, "Pecho"), ex(4, "Pecho"), ex(5, "Pierna"))
        val counts = mapOf(3L to 2, 4L to 9)
        assertEquals(4L, NextExerciseSuggester.suggest(session, catalog, counts))
    }

    @Test
    fun `returns null when no candidate remains in group`() {
        val session = listOf(ex(1, "Pecho"), ex(2, "Pecho"))
        val catalog = session + listOf(ex(5, "Pierna"))
        assertNull(NextExerciseSuggester.suggest(session, catalog, emptyMap()))
    }

    @Test
    fun `breaks group tie using last added exercise`() {
        // 1 Pecho, 1 Pierna → empate; el último añadido es Pierna.
        val session = listOf(ex(1, "Pecho"), ex(2, "Pierna"))
        val catalog = session + listOf(ex(3, "Pecho"), ex(4, "Pierna"))
        assertEquals(4L, NextExerciseSuggester.suggest(session, catalog, emptyMap()))
    }
}
```

- [ ] **Step 2: Pedir al usuario que ejecute el test y confirme que falla**

Pedir: `./gradlew :app:testDebugUnitTest --tests "com.n3k0chan.spotter.ui.workout.NextExerciseSuggesterTest"`
Esperado: FALLA de compilación (no existe `NextExerciseSuggester`). Esperar confirmación.

- [ ] **Step 3: Implementar el sugeridor**

Crear `app/src/main/java/com/n3k0chan/spotter/ui/workout/NextExerciseSuggester.kt`:

```kotlin
package com.n3k0chan.spotter.ui.workout

import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.ui.components.MuscleGroup

/**
 * Elige el siguiente ejercicio a sugerir en una sesión activa:
 * - Solo con ≥2 ejercicios en la sesión.
 * - Grupo objetivo = grupo muscular predominante de la sesión (empate → grupo
 *   del último ejercicio añadido).
 * - Candidato = el más entrenado (trainingCounts) de ese grupo que no esté ya
 *   en la sesión. Sin candidatos → null.
 */
object NextExerciseSuggester {

    fun suggest(
        sessionExercises: List<Exercise>,
        catalog: List<Exercise>,
        trainingCounts: Map<Long, Int>,
    ): Long? {
        if (sessionExercises.size < 2) return null
        val target = predominantGroup(sessionExercises) ?: return null
        val sessionIds = sessionExercises.map { it.id }.toSet()
        val candidates = catalog.filter {
            it.id !in sessionIds && MuscleGroup.from(it.muscleGroup) == target
        }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { trainingCounts[it.id] ?: 0 }?.id
    }

    private fun predominantGroup(sessionExercises: List<Exercise>): MuscleGroup? {
        if (sessionExercises.isEmpty()) return null
        val groups = sessionExercises.map { MuscleGroup.from(it.muscleGroup) }
        val counts = groups.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val top = counts.filterValues { it == max }.keys
        return if (top.size == 1) top.first() else groups.last()
    }
}
```

- [ ] **Step 4: Pedir al usuario que ejecute el test y confirme que pasa**

Pedir: `./gradlew :app:testDebugUnitTest --tests "com.n3k0chan.spotter.ui.workout.NextExerciseSuggesterTest"`
Esperado: PASS (4 tests). Esperar confirmación.

- [ ] **Step 5: Añadir la query de recuentos por ejercicio**

En `WorkoutDao.kt` añadir (junto a las demás queries):

```kotlin
@androidx.room.Query("SELECT exerciseId AS exerciseId, COUNT(*) AS count FROM workout_sets GROUP BY exerciseId")
suspend fun getExerciseSetCounts(): List<ExerciseSetCount>
```

Y la data class de proyección (en el mismo archivo, fuera de la interfaz DAO, o en `entities/Relations.kt` si allí viven las proyecciones — comprobar con `Read` dónde están las demás):

```kotlin
data class ExerciseSetCount(val exerciseId: Long, val count: Int)
```

En `WorkoutRepository.kt` añadir:

```kotlin
suspend fun exerciseTrainingCounts(): Map<Long, Int> =
    dao.getExerciseSetCounts().associate { it.exerciseId to it.count }
```

- [ ] **Step 6: Sustituir el estado de sugerencia IA por el nuevo en `WorkoutViewModel.kt`**

En `WorkoutUiState`, eliminar `suggestion`, `suggestionForExerciseId`, `suggestionLoading` y añadir:

```kotlin
val suggestedExerciseId: Long? = null,
val suggestionDismissed: Boolean = false,
```

Eliminar los métodos `fetchSuggestion(...)` y `clearSuggestion()` (usaban `GroqClient`/`Prompts`). Quitar los imports `com.n3k0chan.spotter.ai.GroqClient` y `com.n3k0chan.spotter.ai.Prompts` (ya sin uso tras Task 2 + este paso).

Añadir un campo cacheado y la lógica de recálculo:

```kotlin
private var trainingCounts: Map<Long, Int> = emptyMap()

fun dismissSuggestion() {
    _state.update { it.copy(suggestionDismissed = true, suggestedExerciseId = null) }
}

private fun recomputeSuggestion() {
    viewModelScope.launch {
        if (trainingCounts.isEmpty()) {
            trainingCounts = runCatching { workouts.exerciseTrainingCounts() }.getOrDefault(emptyMap())
        }
        val catalog = exerciseCatalog.value
        val sessionExercises = _state.value.orderedExerciseIds
            .mapNotNull { id -> catalog.firstOrNull { it.id == id } }
        val suggestedId = NextExerciseSuggester.suggest(sessionExercises, catalog, trainingCounts)
        _state.update { it.copy(suggestedExerciseId = suggestedId) }
    }
}
```

Llamar a `recomputeSuggestion()` y resetear `suggestionDismissed` cuando cambia la composición:
- En `addExerciseToSession(...)`: tras el `_state.update { … }`, poner `_state.update { it.copy(suggestionDismissed = false) }` y llamar `recomputeSuggestion()`.
- En `removeExerciseFromSession(...)`: tras `reload()`, `_state.update { it.copy(suggestionDismissed = false) }` y `recomputeSuggestion()`.
- Al final de `reload()`: llamar `recomputeSuggestion()` (para plantillas que ya traen ejercicios).

- [ ] **Step 7: Quitar el ítem "Sugerencia IA" y la SuggestionCard IA del `ExerciseCard`**

En `WorkoutScreen.kt`:
- Eliminar el `DropdownMenuItem { Text("Sugerencia IA") … onRequestSuggestion() }` (líneas ~339-345).
- Eliminar el bloque `if (suggestionLoading) { … } else if (!suggestion.isNullOrBlank()) { … }` (líneas ~357-363).
- Eliminar el composable `SuggestionCard` (líneas ~580-613) y sus usos.
- En la firma de `ExerciseCard`, eliminar los parámetros `suggestion`, `suggestionLoading`, `onRequestSuggestion`, `onClearSuggestion`. En la llamada a `ExerciseCard` (líneas ~146-165), eliminar esos argumentos.

- [ ] **Step 8: Añadir la tarjeta fantasma al final de la lista**

En la `LazyColumn` de `WorkoutScreen`, tras el bloque `items(state.orderedExerciseIds…)`, añadir:

```kotlin
val suggestedId = state.suggestedExerciseId
if (suggestedId != null && !state.suggestionDismissed) {
    val suggested = catalog.firstOrNull { it.id == suggestedId }
    if (suggested != null) {
        item(key = "ghost-suggestion") {
            GhostSuggestionCard(
                exercise = suggested,
                onAccept = { vm.addExerciseToSession(suggested.id) },
                onCancel = { vm.dismissSuggestion() },
            )
        }
    }
}
```

Añadir el composable (junto a los demás privados del archivo):

```kotlin
@Composable
private fun GhostSuggestionCard(
    exercise: Exercise,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = SpotterTheme.colors
    Box(modifier = Modifier.alpha(0.45f)) {
        SpotterCard(radius = 16.dp, padding = 16.dp, border = c.border) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MuscleGroupAvatar(
                        group = MuscleGroup.from(exercise.muscleGroup),
                        size = 36.dp,
                        iconSize = 18.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SUGERIDO", style = SpotterText.caps, color = c.textMuted)
                        Text(exercise.name, style = SpotterText.title3, color = c.text)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotterButton(
                        text = "Aceptar",
                        leading = Icons.Filled.Add,
                        variant = SpotterButtonVariant.Tonal,
                        modifier = Modifier.weight(1f),
                        onClick = onAccept,
                    )
                    SpotterButton(
                        text = "Cancelar",
                        variant = SpotterButtonVariant.Outlined,
                        onClick = onCancel,
                    )
                }
            }
        }
    }
}
```

Añadir el import `import androidx.compose.ui.draw.alpha` en `WorkoutScreen.kt`.

- [ ] **Step 9: Verificar compilación (el usuario la ejecuta)**

Pedir: `./gradlew :app:compileDebugKotlin`
Esperado: compila. `WorkoutViewModel`/`WorkoutScreen` ya no referencian `ai/`. Esperar confirmación.

- [ ] **Step 10: Commit (el usuario lo ejecuta)**

```bash
git add app/src/main/java/com/n3k0chan/spotter/ui/workout/NextExerciseSuggester.kt \
        app/src/test/java/com/n3k0chan/spotter/workout/NextExerciseSuggesterTest.kt \
        app/src/main/java/com/n3k0chan/spotter/data/db/dao/WorkoutDao.kt \
        app/src/main/java/com/n3k0chan/spotter/data/repository/WorkoutRepository.kt \
        app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutViewModel.kt \
        app/src/main/java/com/n3k0chan/spotter/ui/workout/WorkoutScreen.kt
git commit -m "feat(workout): suggest next exercise by muscle group as ghost card"
```

---

## Task 4: Eliminar el chat asistente

**Files:**
- Delete: `app/src/main/java/com/n3k0chan/spotter/ui/chat/ChatScreen.kt` (+ ViewModel de chat si existe)
- Modify: `ui/nav/Routes.kt`, `ui/nav/SpotterNavGraph.kt`
- Modify: `ui/home/HomeScreen.kt`, `ui/workout/WorkoutHubScreen.kt`, `ui/workout/WorkoutScreen.kt`, `ui/history/HistoryScreen.kt`, `ui/stats/StatsScreen.kt`, `ui/weight/WeightScreen.kt`

- [ ] **Step 1: Localizar todos los puntos de entrada al chat**

Ejecutar (solo lectura):
`Grep pattern="onOpenChat|Routes\\.Chat|ChatScreen|AutoAwesome|ui\\.chat" path="app/src/main/java/com/n3k0chan/spotter" -n`
Anotar cada aparición para eliminarla.

- [ ] **Step 2: Quitar la ruta Chat de la navegación**

En `Routes.kt` eliminar `const val Chat = "chat"`. En `SpotterNavGraph.kt` eliminar el `composable(Routes.Chat) { ChatScreen(...) }` (líneas ~91-93), el import de `ChatScreen` (línea 10), y en cada `composable` eliminar el argumento `onOpenChat = { navController.navigate(Routes.Chat) }` (Home, WorkoutRoot, WorkoutSession, History, Stats, Weight).

- [ ] **Step 3: Quitar el parámetro `onOpenChat` y los botones ✨ de cada pantalla**

Para cada pantalla (`HomeScreen`, `WorkoutHubScreen`, `WorkoutScreen`, `HistoryScreen`, `StatsScreen`, `WeightScreen`):
- Eliminar el parámetro `onOpenChat: () -> Unit` de su firma `@Composable`.
- Eliminar el `SpotterIconButton(icon = Icons.Filled.AutoAwesome, onClick = onOpenChat, …)` de su topbar. En `WorkoutScreen` es el bloque de líneas ~76-81 (dejar el resto del `trailing`).
- Quitar imports de `AutoAwesome` que queden sin uso.

- [ ] **Step 4: Borrar los archivos del chat**

Eliminar `ui/chat/ChatScreen.kt`. Buscar antes un posible ViewModel:
`Grep pattern="class ChatViewModel" path="app/src/main/java/com/n3k0chan/spotter" -n` y borrarlo si existe.

- [ ] **Step 5: Verificar compilación (el usuario la ejecuta)**

Pedir: `./gradlew :app:compileDebugKotlin`
Esperado: compila. Nota: `ChatScreen` usaba `GroqClient`/`Prompts` (paquete `ai/` aún presente hasta Task 6). Si algo del chat referenciaba `chatHistoryWindow`, se limpia en Task 6. Esperar confirmación.

- [ ] **Step 6: Commit (el usuario lo ejecuta)**

```bash
git add -A
git commit -m "refactor(chat): remove AI assistant chat and its entry points"
```

---

## Task 5: Eliminar IA de Home, Peso y Health Connect (import)

**Files:**
- Modify: `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`
- Modify: `ui/weight/WeightViewModel.kt`, `ui/weight/WeightScreen.kt`
- Delete: `ui/health/HealthScreen.kt`, `ui/health/HealthViewModel.kt`
- Modify: `ui/nav/Routes.kt`, `ui/nav/SpotterNavGraph.kt`, `ui/settings/SettingsScreen.kt`, `ui/stats/StatsScreen.kt`

- [ ] **Step 1: Home — frase de bienvenida offline**

`Grep pattern="Groq|Prompts|welcomeMessage|GroqClient" path="app/src/main/java/com/n3k0chan/spotter/ui/home" -n`.
En `HomeViewModel.kt`, sustituir la generación de la frase por IA por el generador offline existente. Comprobar la API disponible con `Read` de `motivation/MotivationalMessages.kt` y usar el método adecuado (p. ej. `MotivationalMessages.home()` o equivalente). Eliminar imports de `ai/`.

- [ ] **Step 2: Peso — quitar análisis IA**

`Grep pattern="Groq|Prompts|weightAnalysis|analiz" path="app/src/main/java/com/n3k0chan/spotter/ui/weight" -in`.
En `WeightViewModel.kt` eliminar el método de análisis IA y su estado; en `WeightScreen.kt` eliminar el botón/sección que lo dispara y lo muestra. Eliminar imports de `ai/`.

- [ ] **Step 3: Health — eliminar la importación de sesiones**

`Grep pattern="HealthScreen|HealthViewModel|Routes\\.Health|onOpenHealth|healthConnectImport|import" path="app/src/main/java/com/n3k0chan/spotter/ui/health" -n`.
- Borrar `ui/health/HealthScreen.kt` y `ui/health/HealthViewModel.kt`.
- En `Routes.kt` eliminar `const val Health = "health"`.
- En `SpotterNavGraph.kt` eliminar `composable(Routes.Health) { HealthScreen(...) }`, el import de `HealthScreen`, y los argumentos `onOpenHealth = { navController.navigate(Routes.Health) }` en `Stats` y `Settings`.
- En `StatsScreen.kt` y `SettingsScreen.kt` eliminar el parámetro `onOpenHealth` de sus firmas y el botón/entrada que abría Health.
- **No tocar** `data/health/HealthConnectManager.kt` ni su uso en `WorkoutViewModel.persistWorkout` (lectura de métricas al terminar — se conserva).

- [ ] **Step 4: Verificar compilación (el usuario la ejecuta)**

Pedir: `./gradlew :app:compileDebugKotlin`. Esperado: compila (el paquete `ai/` sigue hasta Task 6). Esperar confirmación.

- [ ] **Step 5: Commit (el usuario lo ejecuta)**

```bash
git add -A
git commit -m "refactor: remove AI from home, weight analysis and health import"
```

---

## Task 6: Eliminar ajustes de IA, dependencias de red y el paquete `ai/`

**Files:**
- Modify: `data/prefs/SettingsRepository.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `app/build.gradle.kts`, `local.properties.example`, `README.md`
- Delete: `ai/GroqClient.kt`, `ai/GroqService.kt`, `ai/GroqModels.kt`, `ai/Prompts.kt`

- [ ] **Step 1: Limpiar `SettingsRepository.kt`**

Eliminar de `AppSettings`: `groqApiKey`, `isUserOverridingKey`, `groqModel`, `chatHistoryWindow`, y el getter `hasApiKey`. En `load()` quitar su lectura; quitar `setGroqApiKey`, `setModel`, `setChatHistoryWindow`; quitar las constantes `KEY_GROQ_API`, `KEY_MODEL`, `KEY_CHAT_HISTORY_WINDOW`, `DEFAULT_MODEL`, `MODELS`; eliminar el `enum class ChatHistoryWindow` completo; quitar el `import com.n3k0chan.spotter.BuildConfig`.
- Mantener `EncryptedSharedPreferences` (infra de prefs) aunque ya no guarde la key.

- [ ] **Step 2: Limpiar `SettingsScreen.kt`**

`Grep pattern="ApiKey|groqModel|MODELS|ChatHistoryWindow|setGroqApiKey|setModel|API key" path="app/src/main/java/com/n3k0chan/spotter/ui/settings/SettingsScreen.kt" -in`.
Eliminar la sección de API key (campo de texto + guardar/borrar), el selector de modelo y el selector de `ChatHistoryWindow`, junto con sus estados y llamadas al repo.

- [ ] **Step 3: Limpiar Gradle y properties**

`Grep pattern="GROQ_API_KEY|retrofit|okhttp|buildConfigField" path="app/build.gradle.kts" -in`.
En `app/build.gradle.kts`: eliminar el `buildConfigField("String", "GROQ_API_KEY", …)` y las dependencias de Retrofit y OkHttp. **Mantener** `kotlinx-serialization` y su plugin. Si `buildConfig = true` solo se usaba para GROQ_API_KEY, dejarlo si otras cosas lo usan; comprobar con `Grep pattern="BuildConfig\\." path="app/src"`.
En `local.properties.example`: eliminar la línea `GROQ_API_KEY=…`.

- [ ] **Step 4: Borrar el paquete `ai/` y verificar que no quedan referencias**

Eliminar los 4 archivos de `ai/`. Luego:
`Grep pattern="com\\.n3k0chan\\.spotter\\.ai|GroqClient|Prompts\\.|groqApiKey|groqModel|hasApiKey|ChatHistoryWindow" path="app/src" -n`
Esperado: **sin resultados**. Si aparece algo, eliminarlo.

- [ ] **Step 5: Actualizar `README.md`**

Quitar las secciones "API key de Groq" y la mención al "compañero IA … vía Groq" y el `ai/` del árbol de estructura. Ajustar la descripción del stack (quitar Retrofit/OkHttp/Groq). Actualizar la línea de mensajes motivacionales (ahora siempre offline).

- [ ] **Step 6: Verificar build completo y tests (el usuario los ejecuta)**

Pedir:
- `./gradlew :app:testDebugUnitTest` → PASS (8 tests de los dos helpers).
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
Esperar confirmación.

- [ ] **Step 7: Commit (el usuario lo ejecuta)**

```bash
git add -A
git commit -m "refactor: remove Groq AI package, settings and network deps"
```

---

## Self-review (cobertura del spec)

- **Eliminación IA**: paquete `ai/` (Task 6), chat (Task 4), Home welcome (Task 5), análisis peso (Task 5), Health import (Task 5), sugerencia siguiente-serie IA (Task 3), resumen post-entreno IA (Task 2), ajustes API key/modelo (Task 6), deps red + BuildConfig (Task 6). ✔
- **Conservar** INTERNET + serialization (Task 6, Global Constraints) y métricas Health Connect (Task 5, Step 3). ✔
- **Comparación programática**: modelos + helper (Task 1), wiring + persistencia + UI + detalle (Task 2). Criterios (PR, mejora volumen, empate, sin-datos) cubiertos por tests en Task 1. ✔
- **Sugerencia siguiente ejercicio**: helper (Task 3) con grupo predominante, más-entrenado, ≥2 ejercicios, empate por último añadido; tarjeta fantasma semiopaca con Aceptar/Cancelar=solo-ocultar (Task 3, Steps 6-8). ✔
- **Sin migración de BD**: se reutiliza `aiSummaryJson` (Task 2). ✔

## Verificación manual final (smoke)

1. Crear/usar un entreno con ≥2 ejercicios del mismo grupo → aparece tarjeta fantasma semiopaca con un ejercicio de ese grupo. Aceptar lo añade; Cancelar la oculta; añadir otro ejercicio la vuelve a mostrar.
2. Con 1 ejercicio → no aparece tarjeta.
3. Terminar un entreno con historial → diálogo muestra volumen y comparación/PR por ejercicio, sin spinner. Verlo luego en el detalle del historial.
4. Terminar un ejercicio sin historial → "Sin datos previos para comparar".
5. No existe ningún botón/pantalla de chat, análisis de peso, import de Health ni API key en Ajustes.
