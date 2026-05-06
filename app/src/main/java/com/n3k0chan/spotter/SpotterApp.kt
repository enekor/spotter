package com.n3k0chan.spotter

import android.app.Application
import com.n3k0chan.spotter.data.seed.SeedExercises
import com.n3k0chan.spotter.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpotterApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Sembrar el catálogo solo si está vacío. Idempotente: tras la primera vez no hace nada.
        appScope.launch {
            runCatching { SeedExercises.seedIfEmpty(ServiceLocator.exercises) }
        }
    }
}
