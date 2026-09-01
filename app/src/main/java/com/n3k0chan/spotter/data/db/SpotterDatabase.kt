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
import com.n3k0chan.spotter.data.db.dao.WeightDao
import com.n3k0chan.spotter.data.db.entities.Exercise
import com.n3k0chan.spotter.data.db.entities.Template
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.data.db.entities.Workout
import com.n3k0chan.spotter.data.db.entities.WorkoutSet
import com.n3k0chan.spotter.data.db.entities.WeightLog

@Database(
    entities = [
        Exercise::class,
        Template::class,
        TemplateExercise::class,
        Workout::class,
        WorkoutSet::class,
        WeightLog::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun weightDao(): WeightDao

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
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN aiSummaryJson TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weight_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weightKg` REAL NOT NULL, `dateMs` INTEGER NOT NULL, `notes` TEXT)")
            }
        }

        fun get(context: Context): SpotterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SpotterDatabase::class.java,
                "spotter.db",
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }

        fun closeAndClear() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
