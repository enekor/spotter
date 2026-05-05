package com.n3k0chan.spotter

import android.app.Application
import com.n3k0chan.spotter.di.ServiceLocator

class SpotterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
