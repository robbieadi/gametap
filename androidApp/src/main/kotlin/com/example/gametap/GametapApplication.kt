package com.example.gametap

import android.app.Application
import com.example.gametap.di.commonModule
import com.example.gametap.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GametapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GametapApplication)
            modules(commonModule, platformModule())
        }
    }
}
