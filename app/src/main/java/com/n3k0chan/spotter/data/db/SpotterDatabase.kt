package com.n3k0chan.spotter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.n3k0chan.spotter.data.db.dao.ExerciseDao
import com.n3k0chan.spotter.data.db.dao.TemplateDao
import com.n3k0chan.spotter.data.db.dao.WorkoutDao
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.Template
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.data.db.entities.Workout
import com.n3k0chan.spotter.data.db.entities.WorkoutSet

@Database(
    entities = [
        Exercise::class,
        Template::class,
        TemplateExercise::class,
        Workout::class,
        WorkoutSet::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: SpotterDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN calories REAL")
                db.execSQL("ALTER TABLE workouts ADD COLUMN heartRateAvg INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN heartRateMin INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN heartRateMax INTEGER")
                db.execSQL("ALTER TABLE workouts ADD COLUMN distanceMeters REAL")
                db.execSQL("ALTER TABLE workouts ADD COLUMN steps INTEGER")
            }
        }

        fun get(context: Context): SpotterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SpotterDatabase::class.java,
                "spotter.db",
            )
                .addMigrations(MIGRATION_3_4)
                .build()
                .also { instance = it }
        }

        fun closeAndClear() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
