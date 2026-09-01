package com.n3k0chan.spotter.data.repository

import com.n3k0chan.spotter.data.db.dao.WeightDao
import com.n3k0chan.spotter.data.db.entities.WeightLog
import kotlinx.coroutines.flow.Flow

class WeightRepository(private val dao: WeightDao) {
    fun getAllLogs(): Flow<List<WeightLog>> = dao.getAllLogs()
    fun getAllLogsAsc(): Flow<List<WeightLog>> = dao.getAllLogsAsc()

    suspend fun insert(log: WeightLog): Long {
        return dao.insert(log)
    }

    suspend fun update(log: WeightLog) {
        dao.update(log)
    }

    suspend fun delete(log: WeightLog) {
        dao.delete(log)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
