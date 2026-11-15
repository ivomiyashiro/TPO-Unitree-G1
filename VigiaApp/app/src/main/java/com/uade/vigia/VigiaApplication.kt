package com.uade.vigia

import android.app.Application
import com.uade.vigia.di.AppContainer

class VigiaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
