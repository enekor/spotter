package com.n3k0chan.spotter.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Capa fina sobre Health Connect que solo expone lectura.
 * No declaramos ni pedimos jamás permisos WRITE_*.
 */
class HealthConnectRepository(private val context: Context) {

    /** Permisos que pediremos de golpe. Coincide con los uses-permission del manifest. */
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
    )

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.Available
        HealthConnectClient.SDK_UNAVAILABLE -> Availability.Unsupported
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NeedsProvider
        else -> Availability.Unsupported
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun grantedPermissions(): Set<String> = runCatching {
        client().permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(readPermissions)

    suspend fun readToday(zone: ZoneId = ZoneId.systemDefault()): HealthSnapshot {
        if (availability() != Availability.Available) return HealthSnapshot()
        val client = client()
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val now = java.time.Instant.now()
        val dayRange = TimeRangeFilter.between(startOfDay, now)

        // Sueño: ventana = ayer 18:00 → hoy 12:00
        val sleepStart = today.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
        val sleepEnd = today.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant()
        val sleepRange = TimeRangeFilter.between(sleepStart, sleepEnd)

        suspend fun stepsToday() = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            runCatching {
                client.readRecords(ReadRecordsRequest(StepsRecord::class, dayRange))
                    .records.sumOf { it.count }
            }.getOrNull()
        } else null

        suspend fun distanceToday() = if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
            runCatching {
                client.readRecords(ReadRecordsRequest(DistanceRecord::class, dayRange))
                    .records.sumOf { it.distance.inMeters }
            }.getOrNull()
        } else null

        suspend fun caloriesToday() = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            runCatching {
                client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, dayRange))
                    .records.sumOf { it.energy.inKilocalories }
            }.getOrNull()
        } else null

        suspend fun avgHeartRateToday() = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            runCatching {
                val samples = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, dayRange))
                    .records.flatMap { it.samples }
                if (samples.isEmpty()) null else samples.sumOf { it.beatsPerMinute } / samples.size.toDouble()
            }.getOrNull()
        } else null

        suspend fun restingHr() = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
            runCatching {
                client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, dayRange))
                    .records.lastOrNull()?.beatsPerMinute
            }.getOrNull()
        } else null

        suspend fun sleepLastNight() = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            runCatching {
                val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, sleepRange))
                    .records
                if (sessions.isEmpty()) null
                else sessions.maxBy { Duration.between(it.startTime, it.endTime) }
                    .let { Duration.between(it.startTime, it.endTime) }
            }.getOrNull()
        } else null

        suspend fun weight() = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            runCatching {
                val range = TimeRangeFilter.between(
                    today.minusDays(30).atStartOfDay(zone).toInstant(),
                    now,
                )
                client.readRecords(ReadRecordsRequest(WeightRecord::class, range))
                    .records.maxByOrNull { it.time }?.weight?.inKilograms
            }.getOrNull()
        } else null

        suspend fun bodyFat() = if (HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
            runCatching {
                val range = TimeRangeFilter.between(
                    today.minusDays(30).atStartOfDay(zone).toInstant(),
                    now,
                )
                client.readRecords(ReadRecordsRequest(BodyFatRecord::class, range))
                    .records.maxByOrNull { it.time }?.percentage?.value
            }.getOrNull()
        } else null

        return HealthSnapshot(
            steps = stepsToday(),
            distanceMeters = distanceToday(),
            activeKcal = caloriesToday(),
            avgHeartRateBpm = avgHeartRateToday(),
            restingHeartRateBpm = restingHr(),
            sleepLastNight = sleepLastNight(),
            weightKg = weight(),
            bodyFatPct = bodyFat(),
        )
    }

    enum class Availability { Available, NeedsProvider, Unsupported }
}

data class HealthSnapshot(
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeKcal: Double? = null,
    val avgHeartRateBpm: Double? = null,
    val restingHeartRateBpm: Long? = null,
    val sleepLastNight: Duration? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
) {
    val isEmpty: Boolean
        get() = steps == null && distanceMeters == null && activeKcal == null &&
            avgHeartRateBpm == null && restingHeartRateBpm == null &&
            sleepLastNight == null && weightKg == null && bodyFatPct == null

    /** Resumen compacto para inyectar en prompts de IA. */
    fun toPromptContext(): String = buildString {
        steps?.let { appendLine("- Pasos hoy: $it") }
        distanceMeters?.let { appendLine("- Distancia hoy: ${"%.1f".format(it / 1000)} km") }
        activeKcal?.let { appendLine("- Calorías activas hoy: ${it.toInt()} kcal") }
        avgHeartRateBpm?.let { appendLine("- FC media hoy: ${it.toInt()} bpm") }
        restingHeartRateBpm?.let { appendLine("- FC en reposo: $it bpm") }
        sleepLastNight?.let {
            val h = it.toHours()
            val m = (it.toMinutes() % 60)
            appendLine("- Sueño anoche: ${h}h ${m}min")
        }
        weightKg?.let { appendLine("- Peso reciente: ${"%.1f".format(it)} kg") }
        bodyFatPct?.let { appendLine("- Grasa corporal: ${"%.1f".format(it)}%") }
    }.trimEnd()
}
