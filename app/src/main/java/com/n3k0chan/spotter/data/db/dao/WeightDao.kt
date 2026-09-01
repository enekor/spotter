package com.n3k0chan.spotter.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.n3k0chan.spotter.data.db.entities.WeightLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_logs ORDER BY dateMs DESC")
    fun getAllLogs(): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_logs ORDER BY dateMs ASC")
    fun getAllLogsAsc(): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WeightLog): Long

    @Update
    suspend fun update(log: WeightLog)

    @Delete
    suspend fun delete(log: WeightLog)

    @Query("DELETE FROM weight_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
