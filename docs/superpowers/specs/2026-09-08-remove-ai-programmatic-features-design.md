# Eliminar IA + comparación y sugerencia programáticas

Fecha: 2026-09-08
Estado: aprobado (diseño)

## Objetivo

Retirar por completo las funciones basadas en IA (Groq/LLM) de Spotter y
sustituir dos de ellas por lógica programática local:

1. La **comparación al terminar el entreno** pasa a calcularse en el dispositivo
   (menos rica que un LLM, pero útil y offline).
2. La **sugerencia de siguiente ejercicio** pasa a ser una recomendación local
   basada en el grupo muscular de la sesión, presentada como una "tarjeta
   fantasma" semiopaca con Aceptar/Cancelar.

Todo el resto de la IA se elimina sin sustituto (o cae al fallback offline que
ya existe).

## 1. Eliminación de la IA

Se borra todo lo que dependa de Groq/LLM:

- **Paquete `ai/` completo**: `GroqClient`, `GroqService`, `GroqModels`,
  `Prompts`.
- **Chat asistente**: `ui/chat/ChatScreen.kt` y su `ViewModel`, la ruta en
  `ui/nav/Routes.kt` y su entrada en `ui/nav/SpotterNavGraph.kt`, el parámetro
  `onOpenChat` allí donde se propaga, y el botón `AutoAwesome` del topbar de
  `WorkoutScreen` (y cualquier otro punto de entrada al chat).
- **Home**: la frase de bienvenida generada por IA en `HomeViewModel` cae al
  generador offline existente (`motivation/MotivationalMessages`).
- **Peso**: se elimina el análisis IA de `WeightViewModel` / `WeightScreen`
  (botón y sección de análisis).
- **Health Connect**: se elimina **la importación de sesiones entera** (pantalla,
  flujo y etiquetado IA). Incluye `ui/health/HealthScreen.kt`,
  `ui/health/HealthViewModel.kt`, su ruta y navegación, y el uso de
  `Prompts.healthConnectImport`.
  - **Se conserva** `data/health/HealthConnectManager` y la lectura de métricas
    (calorías, pulso, distancia, pasos) al terminar el entreno en
    `WorkoutViewModel.persistWorkout`. Eso no es IA.
- **Ajustes**: se retira de `SettingsScreen` y `SettingsRepository` todo lo
  relativo a la API key, `groqModel`, `groqApiKey`, `hasApiKey` y el
  almacenamiento cifrado de la clave. El ítem de menú "Sugerencia IA" del
  `ExerciseCard` desaparece.
- **Build / manifest**:
  - Fuera Retrofit, OkHttp y el cableado de `BuildConfig.GROQ_API_KEY` en
    `build.gradle.kts` y `local.properties(.example)`.
  - **Se mantiene** `kotlinx-serialization` (se reutiliza para la comparación).
  - **Se mantiene** el permiso `INTERNET` (lo usa el backup de Drive).

### Criterio de aceptación (eliminación)

- El proyecto compila sin ninguna referencia a `com.n3k0chan.spotter.ai`.
- No quedan referencias a `GroqClient`, `Prompts`, `groqApiKey`, `groqModel`,
  `hasApiKey`.
- No hay ninguna pantalla ni botón que abra el chat.
- La app no realiza ninguna llamada de red a Groq.

## 2. Comparación programática al terminar

### Modelo de datos

Se renombran las clases `AiSummaryResponse` / `AiSummaryExercise`
(en `WorkoutViewModel.kt`) a `WorkoutComparison` / `ComparisonExercise`,
**manteniendo las mismas claves JSON** (`summary`, `exercises`, `name`,
`markdown`) mediante `@SerialName` si hace falta, para que los resúmenes ya
guardados en `Workout.aiSummaryJson` sigan siendo legibles.

```kotlin
@Serializable
data class WorkoutComparison(
    val summary: String,
    val exercises: List<ComparisonExercise> = emptyList(),
)

@Serializable
data class ComparisonExercise(
    val name: String,
    val markdown: String, // texto plano/ligero; el diálogo lo pinta como Text
)
```

La columna `Workout.aiSummaryJson` **se conserva sin migración** (se repurposa
para almacenar la comparación programática).

### Cálculo

En `WorkoutViewModel.finish()`, tras persistir el entreno, se construye la
comparación de forma síncrona (sin estado `loading`). Para cada ejercicio de la
sesión:

- Se recuperan las series históricas con
  `workouts.recentSetsFor(exerciseId, limit = 30)` y **se excluye el entreno
  actual** filtrando por `WorkoutSet.workoutId`.
- **Total de hoy**: nº de series y volumen según el perfil de medida:
  - `WeightReps` → Σ (peso × reps)
  - `Reps` → Σ reps
  - `Duration` / `WeightDuration` → Σ segundos
  - `DistanceTime` / `CardioMachine` / `TreadmillIncline` → Σ distancia
- **Mejor serie de hoy** vs. **mejor serie histórica** (misma heurística que
  `WorkoutRepository.bestSetFor`, pero sobre el histórico sin hoy) → detección
  de **PR**.
- **Deltas** de volumen y de mejor serie frente a la sesión previa más reciente
  de ese ejercicio.
- Veredicto por ejercicio con plantillas de texto, p. ej.:
  - `"Nuevo PR: 80kg×5 (antes 77.5kg×5)"`
  - `"Volumen 3.200 kg (+8% vs última sesión)"`
  - `"Igual que la última vez"`
  - `"Sin datos previos para comparar"`
- `summary` general: totales del entreno (nº de ejercicios, nº de series,
  nº de PRs).

La lógica de cálculo vive en un helper puro y testeable (p. ej.
`workout/WorkoutComparator.kt`) que recibe las series de hoy + histórico por
ejercicio y devuelve `WorkoutComparison`. `WorkoutViewModel` solo orquesta las
consultas al repo y llama al helper.

### UI

- `PostFinishSummaryDialog` se mantiene con su pager por ejercicio, alimentado
  ahora por datos locales. Como el cálculo es instantáneo, el estado
  `finishedLoading` deja de ser necesario (o queda siempre `false`).
- El icono decorativo `AutoAwesome` se sustituye por uno neutro (p. ej.
  `TrendingUp`).
- En `WorkoutDetailScreen`, las etiquetas "IA" / "Resumen IA" pasan a
  "Comparación".

### Criterio de aceptación (comparación)

- Al terminar un entreno con historial previo, el diálogo muestra por ejercicio
  el volumen de hoy y su comparación (delta/PR) sin llamadas de red.
- Un entreno sin historial previo muestra "Sin datos previos para comparar".
- La comparación se persiste en `aiSummaryJson` y se ve luego en el detalle del
  entreno.
- El helper de cálculo tiene tests unitarios para: PR nuevo, mejora de volumen,
  empate, y sin-datos-previos.

## 3. Sugerencia de siguiente ejercicio (tarjeta fantasma)

### Lógica (en `WorkoutViewModel`)

Se recalcula cada vez que cambia la composición de la sesión
(`orderedExerciseIds`):

- Solo si hay **≥2 ejercicios** en la sesión.
- **Grupo objetivo = grupo predominante**: la moda de
  `MuscleGroup.from(exercise.muscleGroup)` entre los ejercicios ya puestos.
  Empate → grupo del **último ejercicio añadido**.
- **Ejercicio candidato**: dentro de ese grupo, el **más entrenado** en el
  histórico del usuario que **no esté ya** en la sesión.
  - Fallback: si ninguno del grupo tiene histórico, cualquiera del catálogo de
    ese grupo no añadido.
  - Si no hay ningún candidato en ese grupo → no se muestra tarjeta.
- Flag `suggestionDismissed`: **Cancelar** lo pone a `true` y oculta la tarjeta;
  se resetea a `false` cuando cambia la composición de la sesión (p. ej. añades
  otro ejercicio), permitiendo que vuelva a aparecer.

Estado nuevo en `WorkoutUiState` (sustituye al estado de sugerencia IA de
"siguiente serie"): `suggestedExerciseId: Long?` y `suggestionDismissed: Boolean`.
Se eliminan `suggestion`, `suggestionForExerciseId`, `suggestionLoading` y el
método `fetchSuggestion` (IA).

### UI

Al final de la `LazyColumn` de `WorkoutScreen`, cuando hay sugerencia visible,
se pinta una tarjeta con estilo de `ExerciseCard` pero **semiopaca (~40% alpha)**
que muestra el avatar del grupo muscular + el nombre del ejercicio propuesto, y
dos botones:

- **Aceptar** → `vm.addExerciseToSession(id)`; al cambiar la composición se
  recalcula (puede proponer otro ejercicio).
- **Cancelar** → `vm.dismissSuggestion()` (solo oculta).

### Criterio de aceptación (sugerencia)

- Con 0 o 1 ejercicios en la sesión no aparece ninguna tarjeta fantasma.
- Con ≥2 ejercicios aparece una tarjeta semiopaca con un ejercicio del grupo
  predominante que no está en la sesión.
- Aceptar añade ese ejercicio y (si procede) propone el siguiente.
- Cancelar oculta la tarjeta; reaparece al añadir otro ejercicio.

## Fuera de alcance

- No se añade renderizado real de Markdown (el texto de comparación es plano).
- No se cambia el esquema de la BD (se reutiliza `aiSummaryJson`).
- No se toca el backup de Drive ni el temporizador de descanso.
