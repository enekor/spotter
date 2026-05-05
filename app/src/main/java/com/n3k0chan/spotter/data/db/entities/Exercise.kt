package com.n3k0chan.spotter.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
