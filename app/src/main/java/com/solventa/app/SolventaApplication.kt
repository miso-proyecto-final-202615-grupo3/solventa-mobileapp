package com.solventa.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.solventa.app.di.AppContainer
import com.solventa.app.core.push.ClaimNotifications
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SolventaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ClaimNotifications.createChannel(this)
        // Login y desbloqueo guardan la sesión: en ambos casos se registra/renueva el token FCM.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            container.sessionStore.session.filterNotNull().collect { container.pushRegistrar.register() }
        }
        container.appLockManager.onColdStart()
        // HU4.1.2: retoma las evidencias sin enviar (se envían cuando hay sesión) y, ya cargadas, borra los
        // archivos huérfanos (capturas canceladas o restos de un cierre).
        container.evidenceUploadQueue.start(onLoaded = container.evidenceFileStore::purgeExcept)
        // HU3.2.x: carga la billetera guardada y la mantiene sincronizada.
        container.walletSync.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.appLockManager.onAppForegrounded()
                override fun onStop(owner: LifecycleOwner) = container.appLockManager.onAppBackgrounded()
            },
        )
    }
}
