package com.n3k0chan.spotter.di

import android.content.Context
import com.n3k0chan.spotter.backup.DriveBackupManager
import com.n3k0chan.spotter.data.db.SpotterDatabase
import com.n3k0chan.spotter.data.health.HealthConnectManager
import com.n3k0chan.spotter.data.prefs.SettingsRepository
import com.n3k0chan.spotter.data.repository.ExerciseRepository
import com.n3k0chan.spotter.data.repository.TemplateRepository
import com.n3k0chan.spotter.data.repository.WorkoutRepository

/**
 * Service locator simple. Evita Hilt para mantener el proyecto sin annotation processors extra.
 * Todos los repositorios son singletons asociados al Application context.
 */
object ServiceLocator {

    @Volatile private var initialized = false

    lateinit var settings: SettingsRepository
        private set
    lateinit var exercises: ExerciseRepository
        private set
    lateinit var templates: TemplateRepository
        private set
    lateinit var workouts: WorkoutRepository
        private set
    lateinit var driveBackup: DriveBackupManager
        private set
    lateinit var healthConnect: HealthConnectManager
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val db = SpotterDatabase.get(app)
            settings = SettingsRepository(app)
            exercises = ExerciseRepository(db.exerciseDao())
            templates = TemplateRepository(db.templateDao())
            workouts = WorkoutRepository(db.workoutDao())
            driveBackup = DriveBackupManager(app, settings)
            healthConnect = HealthConnectManager(app)
            initialized = true
        }
    }
}
