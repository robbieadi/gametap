package io.github.robbieadi.gametap

import android.app.Application
import io.github.robbieadi.gametap.di.commonModule
import io.github.robbieadi.gametap.di.platformModule
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
