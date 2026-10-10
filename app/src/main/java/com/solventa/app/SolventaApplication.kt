package com.solventa.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.solventa.app.di.AppContainer

class SolventaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appLockManager.onColdStart()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.appLockManager.onAppForegrounded()
                override fun onStop(owner: LifecycleOwner) = container.appLockManager.onAppBackgrounded()
            },
        )
    }
}
