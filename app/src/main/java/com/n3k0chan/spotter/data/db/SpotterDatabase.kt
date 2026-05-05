package com.n3k0chan.spotter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false,
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: SpotterDatabase? = null

        fun get(context: Context): SpotterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SpotterDatabase::class.java,
                "spotter.db",
            ).build().also { instance = it }
        }

        /**
         * Cierra la instancia activa de Room. Llamar antes de sobrescribir el
         * fichero spotter.db desde un backup remoto. Después de restaurar es
         * necesario reiniciar el proceso para reabrir la BD limpia y reinstanciar
         * los repositorios.
         */
        fun closeAndClear() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
