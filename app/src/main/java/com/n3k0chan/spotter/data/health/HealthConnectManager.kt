package com.n3k0chan.spotter.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HeartRateSummary(val min: Long, val max: Long, val avg: Long)

data class ExerciseSession(
    val title: String?,
    val type: Int,
    val startTime: Instant,
    val endTime: Instant,
    val calories: Double?,
    val distance: Double?,
    val heartRateAvg: Long?,
    val sourcePackage: String? = null,
)

data class SleepSession(
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Long,
)

data class WorkoutHealthMetrics(
    val calories: Double?,
    val heartRateAvg: Long?,
    val heartRateMin: Long?,
    val heartRateMax: Long?,
    val distanceMeters: Double?,
    val steps: Long?,
)

data class DaySummary(
    val date: LocalDate,
    val steps: Long?,
    val totalCalories: Double?,
    val activeCalories: Double?,
    val heartRate: HeartRateSummary?,
    val distanceMeters: Double?,
    val exerciseSessions: List<ExerciseSession>,
    val sleepSessions: List<SleepSession>,
)

class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun isAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    private fun getClient(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        val client = getClient()
        val granted = client.permissionController.getGrantedPermissions()
        return permissions.all { it in granted }
    }

    suspend fun readDaySummary(date: LocalDate): DaySummary {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(start, end)
        val client = getClient()

        val steps = runCatching {
            client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), filter)
            )[StepsRecord.COUNT_TOTAL]
        }.getOrNull()

        val totalCalories = runCatching {
            client.aggregate(
                AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), filter)
            )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        }.getOrNull()

        val activeCalories = runCatching {
            client.aggregate(
                AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), filter)
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        }.getOrNull()

        val heartRate = runCatching {
            val hr = client.aggregate(
                AggregateRequest(
                    setOf(
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX,
                        HeartRateRecord.BPM_AVG,
                    ),
                    filter,
                )
            )
            val min = hr[HeartRateRecord.BPM_MIN]
            val max = hr[HeartRateRecord.BPM_MAX]
            val avg = hr[HeartRateRecord.BPM_AVG]
            if (min != null && max != null && avg != null) HeartRateSummary(min, max, avg) else null
        }.getOrNull()

        val distance = runCatching {
            client.aggregate(
                AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), filter)
            )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
        }.getOrNull()

        val exerciseSessions = runCatching {
            val records = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, filter)
            ).records
            records.map { r ->
                val sessionFilter = TimeRangeFilter.between(r.startTime, r.endTime)
                val cal = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), sessionFilter)
                    )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                }.getOrNull()
                val dist = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), sessionFilter)
                    )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                }.getOrNull()
                val hrAvg = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(HeartRateRecord.BPM_AVG), sessionFilter)
                    )[HeartRateRecord.BPM_AVG]
                }.getOrNull()
                ExerciseSession(
                    title = r.title,
                    type = r.exerciseType,
                    startTime = r.startTime,
                    endTime = r.endTime,
                    calories = cal,
                    distance = dist,
                    heartRateAvg = hrAvg,
                    sourcePackage = r.metadata.dataOrigin.packageName,
                )
            }
        }.getOrDefault(emptyList())

        val sleepSessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, filter)
            ).records.map { r ->
                val dur = java.time.Duration.between(r.startTime, r.endTime).toMinutes()
                SleepSession(
                    startTime = r.startTime,
                    endTime = r.endTime,
                    durationMinutes = dur,
                )
            }
        }.getOrDefault(emptyList())

        return DaySummary(
            date = date,
            steps = steps,
            totalCalories = totalCalories,
            activeCalories = activeCalories,
            heartRate = heartRate,
            distanceMeters = distance,
            exerciseSessions = exerciseSessions,
            sleepSessions = sleepSessions,
        )
    }

    suspend fun readExerciseSessions(startDate: LocalDate, endDate: LocalDate): List<ExerciseSession> {
        val zone = ZoneId.systemDefault()
        val start = startDate.atStartOfDay(zone).toInstant()
        val end = endDate.plusDays(1).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(start, end)
        val client = getClient()

        return runCatching {
            val records = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, filter)
            ).records
            records.map { r ->
                val sessionFilter = TimeRangeFilter.between(r.startTime, r.endTime)
                val cal = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), sessionFilter)
                    )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                }.getOrNull()
                val dist = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), sessionFilter)
                    )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                }.getOrNull()
                val hrAvg = runCatching {
                    client.aggregate(
                        AggregateRequest(setOf(HeartRateRecord.BPM_AVG), sessionFilter)
                    )[HeartRateRecord.BPM_AVG]
                }.getOrNull()
                ExerciseSession(
                    title = r.title,
                    type = r.exerciseType,
                    startTime = r.startTime,
                    endTime = r.endTime,
                    calories = cal,
                    distance = dist,
                    heartRateAvg = hrAvg,
                    sourcePackage = r.metadata.dataOrigin.packageName,
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun readMetricsForTimeRange(startInstant: Instant, endInstant: Instant): WorkoutHealthMetrics? {
        val filter = TimeRangeFilter.between(startInstant, endInstant)
        val client = getClient()

        val calories = runCatching {
            client.aggregate(
                AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), filter)
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        }.getOrNull()

        val hr = runCatching {
            val agg = client.aggregate(
                AggregateRequest(
                    setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_AVG),
                    filter,
                )
            )
            Triple(agg[HeartRateRecord.BPM_MIN], agg[HeartRateRecord.BPM_MAX], agg[HeartRateRecord.BPM_AVG])
        }.getOrNull()

        val distance = runCatching {
            client.aggregate(
                AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), filter)
            )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
        }.getOrNull()

        val steps = runCatching {
            client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), filter)
            )[StepsRecord.COUNT_TOTAL]
        }.getOrNull()

        val hasAnyData = calories != null || hr?.third != null || distance != null || steps != null
        if (!hasAnyData) return null

        return WorkoutHealthMetrics(
            calories = calories,
            heartRateAvg = hr?.third,
            heartRateMin = hr?.first,
            heartRateMax = hr?.second,
            distanceMeters = distance,
            steps = steps,
        )
    }
}
