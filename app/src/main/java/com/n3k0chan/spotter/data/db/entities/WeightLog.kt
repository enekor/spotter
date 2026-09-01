package com.n3k0chan.spotter.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_logs")
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Float,
    val dateMs: Long,
    val notes: String? = null
)
